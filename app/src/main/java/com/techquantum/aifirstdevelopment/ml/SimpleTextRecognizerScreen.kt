package com.techquantum.aifirstdevelopment.ml

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import androidx.compose.ui.tooling.preview.Preview as ComposePreview

data class TextPositionItem(
    val id: String,
    val text: String,
    val boundingBox: Rect,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextRecognizerScreen() {
    val context = LocalContext.current

    // --- UI States ---
    var showCameraView by remember { mutableStateOf(false) }
    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedLineItems by remember { mutableStateOf<List<TextPositionItem>>(emptyList()) }
    var fullRecognizedText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ML Kit Recognizer instance
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Process Bitmap with ML Kit OCR
    fun processBitmapImage(bitmap: Bitmap) {
        isLoading = true
        errorMessage = null
        detectedLineItems = emptyList()
        fullRecognizedText = ""

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                isLoading = false
                fullRecognizedText = visionText.text

                val items = mutableListOf<TextPositionItem>()
                var counter = 0
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        line.boundingBox?.let { rect ->
                            items.add(
                                TextPositionItem(
                                    id = "line_${counter++}",
                                    text = line.text,
                                    boundingBox = rect,
                                ),
                            )
                        }
                    }
                }

                detectedLineItems = items
                if (visionText.text.isBlank()) {
                    errorMessage = "No text found in this image!"
                }
            }
            .addOnFailureListener { exception ->
                isLoading = false
                errorMessage = "Recognition failed: ${exception.localizedMessage}"
            }
    }

    // Helper function to rotate bitmap to correct orientation angle
    fun rotateBitmap(rawBitmap: Bitmap, rotationDegrees: Int): Bitmap {
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }
    }

    // Function to load gallery bitmap
    fun loadGalleryBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Photo Picker Launcher (Gallery)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = loadGalleryBitmap(uri)
            if (bitmap != null) {
                activeBitmap = bitmap
                processBitmapImage(bitmap)
            } else {
                Toast.makeText(context, "Image load error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            showCameraView = true
        } else {
            Toast.makeText(context, "Camera permission is required!", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to check permission before launching in-app camera
    fun openInAppCamera() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            showCameraView = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showCameraView) {
            // Fast In-App CameraX Screen (Zero system prompt screen, unbinds hardware on capture)
            InAppCameraCaptureView(
                onImageCaptured = { imageProxy ->
                    showCameraView = false
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val rawBitmap = imageProxy.toBitmap()
                    imageProxy.close()

                    // Rotate bitmap to exact upright angle
                    val orientedBitmap = rotateBitmap(rawBitmap, rotation)
                    activeBitmap = orientedBitmap
                    processBitmapImage(orientedBitmap)
                },
                onClose = {
                    showCameraView = false
                },
            )
        } else if (activeBitmap != null) {
            // Exact On-Image Selectable Text Overlay Screen (NO Bottom Sheet!)
            ExactOnImageTextOverlayView(
                bitmap = activeBitmap!!,
                textItems = detectedLineItems,
                fullText = fullRecognizedText,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onRetake = {
                    activeBitmap = null
                    detectedLineItems = emptyList()
                    fullRecognizedText = ""
                    errorMessage = null
                    openInAppCamera()
                },
                onClose = {
                    activeBitmap = null
                    detectedLineItems = emptyList()
                    fullRecognizedText = ""
                    errorMessage = null
                },
            )
        } else {
            // Main OCR Dashboard Screen
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("On-Device ML Kit OCR") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                },
                floatingActionButtonPosition = FabPosition.Center,
                floatingActionButton = {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Select Image Button (Gallery)
                            ExtendedFloatingActionButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                icon = { Icon(Icons.Default.Image, contentDescription = null) },
                                text = { Text("Gallery") },
                                expanded = !isLoading,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )

                            // Capture Image Button (In-App Camera)
                            ExtendedFloatingActionButton(
                                onClick = {
                                    openInAppCamera()
                                },
                                icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                                text = { Text("Take Photo") },
                                expanded = !isLoading,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                },
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Placeholder prompt card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Take a photo or select from Gallery",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Text will be positioned directly over the photo. Touch and drag across any text to select and copy.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * On-Image Selectable Text Overlay View:
 * Positions selectable text elements directly on top of the image at their exact coordinates. No bottom sheet!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExactOnImageTextOverlayView(
    bitmap: Bitmap,
    textItems: List<TextPositionItem>,
    fullText: String,
    isLoading: Boolean,
    errorMessage: String?,
    onRetake: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

    // Zoom & Pan gesture states
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text Inspector") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    // Copy All Extracted Text
                    if (fullText.isNotBlank()) {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(fullText))
                            Toast.makeText(context, "All text copied!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text")
                        }
                    }

                    // Retake / New Photo
                    IconButton(onClick = onRetake) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retake")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
                .onGloballyPositioned { containerSize = it.size },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Detecting text position...", color = Color.White)
                }
            } else {
                // Interactive Zoomable Container for Photo & Text Overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale == 1f) {
                                    offset = Offset.Zero
                                } else {
                                    offset += pan
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Layer 1: High-Res Photo
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured Photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                            ),
                        contentScale = ContentScale.Fit,
                    )

                    // Layer 2: On-Image Selectable Text Overlay at Exact Bounding Box Coordinates
                    if (containerSize.width > 0 && containerSize.height > 0 && textItems.isNotEmpty()) {
                        val imgWidth = bitmap.width.toFloat()
                        val imgHeight = bitmap.height.toFloat()

                        val scaleFactor = minOf(
                            containerSize.width.toFloat() / imgWidth,
                            containerSize.height.toFloat() / imgHeight,
                        )

                        val offsetX = (containerSize.width.toFloat() - imgWidth * scaleFactor) / 2f
                        val offsetY = (containerSize.height.toFloat() - imgHeight * scaleFactor) / 2f

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ),
                        ) {
                            SelectionContainer {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    for (item in textItems) {
                                        val leftPx = offsetX + item.boundingBox.left * scaleFactor
                                        val topPx = offsetY + item.boundingBox.top * scaleFactor
                                        val widthPx = item.boundingBox.width() * scaleFactor
                                        val heightPx = item.boundingBox.height() * scaleFactor

                                        val leftDp = with(density) { leftPx.toDp() }
                                        val topDp = with(density) { topPx.toDp() }
                                        val widthDp = with(density) { widthPx.toDp() }

                                        val fontSizeSp = (heightPx * 0.7f / density.density).coerceAtLeast(10f).sp

                                        Text(
                                            text = item.text,
                                            style = TextStyle(
                                                fontSize = fontSizeSp,
                                                color = Color.Transparent,
                                                background = Color.Transparent,
                                            ),
                                            modifier = Modifier
                                                .offset(x = leftDp, y = topDp)
                                                .width(widthDp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fast In-App CameraX Screen designed for zero-latency capture and zero external system camera prompts.
 */
@Composable
fun InAppCameraCaptureView(
    onImageCaptured: (ImageProxy) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var isCapturing by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // CameraX Live Preview View
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture,
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top Controls Header (Back button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
        }

        // Bottom Controls Footer (Fast Shutter Button)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isCapturing) {
                CircularProgressIndicator(color = Color.White)
            } else {
                IconButton(
                    onClick = {
                        isCapturing = true
                        imageCapture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    onImageCaptured(image)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    Toast.makeText(
                                        context,
                                        "Capture failed: ${exception.localizedMessage}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    },
                    modifier = Modifier
                        .size(76.dp)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color.Gray.copy(alpha = 0.6f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Capture",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }
}

@ComposePreview
@Composable
fun TextRecognizerScreenPreview() {
    TextRecognizerScreen()
}