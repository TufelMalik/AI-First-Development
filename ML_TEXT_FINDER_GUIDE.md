# Repository Analysis: flutter-ml-text-finder & Android Implementation Guide

This document contains a comprehensive breakdown of the [Dinesh-Sowndar/flutter-ml-text-finder](https://github.com/Dinesh-Sowndar/flutter-ml-text-finder) repository, detailing the libraries used, code architecture, and step-by-step instructions to implement the equivalent real-time text finder feature in Android using Jetpack Compose, CameraX, and Google ML Kit.

---

## 1. Overview of `flutter-ml-text-finder`

The application allows users to search for specific text in real time using a live camera feed. When matching text is detected in the frame, it dynamically highlights a bounding box around the text directly on top of the live camera view.

---

## 2. Tech Stack & Dependencies Used

| Layer | Package / Library | Description |
| :--- | :--- | :--- |
| **ML Engine** | `google_mlkit_text_recognition: 0.4.0` | Google ML Kit On-Device Text Recognition plugin for Flutter |
| **Camera Stream** | `camera: ^0.10.0+4` | Live camera preview feed & frame byte streaming (`startImageStream`) |
| **UI Icons** | `cupertino_icons: ^1.0.2` | iOS style icons for Flutter UI |
| **UI Canvas Overlay** | Flutter `CustomPainter` | Draws bounding box rectangles (`Canvas.drawRect`) and text labels over the camera feed |

---

## 3. Key Code Components & Logic in Flutter

1. **`camera_view.dart`**:
   - Initializes `CameraController` with camera stream (`ResolutionPreset.high`).
   - Converts raw camera frames (`CameraImage`) into ML Kit `InputImage` format (extracting width, height, sensor rotation, and plane bytes).
   - Includes zoom level slider controls (`setZoomLevel`).

2. **`text_detector.dart`**:
   - Manages `TextRecognizer(script: TextRecognitionScript.chinese)` instance.
   - Throttles parallel frame processing using an `_isBusy` flag to prevent frame lag.
   - Passes ML Kit `RecognizedText` to `TextRecognizerPainter`.

3. **`text_detector_paint.dart`**:
   - Iterates through ML Kit `textBlocks` $\rightarrow$ `lines` $\rightarrow$ `elements`.
   - Performs case-insensitive matching (`element.text.toLowerCase().contains(text.toLowerCase())`).
   - Maps raw image coordinates to screen canvas size (`translateX`, `translateY`).
   - Draws a red bounding box (`canvas.drawRect`) and translucent background text label on matched elements.

---

## 4. How to Implement Real-Time Text Finder in Android

### Step 1: Add CameraX Dependencies (`app/build.gradle.kts`)

```kotlin
dependencies {
    // Google ML Kit Text Recognition (On-Device)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // CameraX
    val cameraVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
}
```

---

### Step 2: Architecture & Data Flow

```
[ CameraX ImageAnalysis ] ──(ImageProxy @ 30 FPS)──> [ Convert to ML Kit InputImage ]
                                                              │
                                                              ▼
[ Compose Canvas Overlay ] <──(Filtered Bounding Boxes)─── [ TextRecognition.getClient ]
```

---

### Step 3: Complete Jetpack Compose Implementation

Below is the complete, self-contained implementation combining CameraX live stream, ML Kit Text Recognition, and Jetpack Compose canvas overlay:

```kotlin
package com.techquantum.aifirstdevelopment.ml

import android.graphics.RectF
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@Composable
fun LiveTextFinderScreen(
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. ML Kit Recognizer Instance
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // States for holding bounding boxes and image dimensions
    var matchedBoxes by remember { mutableStateOf<List<android.graphics.Rect>>(emptyList()) }
    var imageWidth by remember { mutableIntStateOf(1) }
    var imageHeight by remember { mutableIntStateOf(1) }
    var isBusy by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 2. CameraX Live Preview View
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // Set up ImageAnalysis analyzer with backpressure strategy
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !isBusy) {
                            isBusy = true
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

                            // Swap width & height for landscape/portrait rotation
                            if (rotationDegrees == 90 || rotationDegrees == 270) {
                                imageWidth = mediaImage.height
                                imageHeight = mediaImage.width
                            } else {
                                imageWidth = mediaImage.width
                                imageHeight = mediaImage.height
                            }

                            // Process frame with ML Kit
                            recognizer.process(inputImage)
                                .addOnSuccessListener { visionText ->
                                    val matches = mutableListOf<android.graphics.Rect>()
                                    for (block in visionText.textBlocks) {
                                        for (line in block.lines) {
                                            for (element in line.elements) {
                                                if (searchQuery.isNotBlank() &&
                                                    element.text.contains(searchQuery, ignoreCase = true)
                                                ) {
                                                    element.boundingBox?.let { matches.add(it) }
                                                }
                                            }
                                        }
                                    }
                                    matchedBoxes = matches
                                }
                                .addOnCompleteListener {
                                    isBusy = false
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 3. Compose Overlay for Highlighting Bounding Boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / imageWidth.toFloat()
            val scaleY = size.height / imageHeight.toFloat()

            for (rect in matchedBoxes) {
                val scaledRect = RectF(
                    rect.left * scaleX,
                    rect.top * scaleY,
                    rect.right * scaleX,
                    rect.bottom * scaleY
                )

                drawRect(
                    color = Color.Red,
                    topLeft = Offset(scaledRect.left, scaledRect.top),
                    size = Size(scaledRect.width(), scaledRect.height()),
                    style = Stroke(width = 6f)
                )
            }
        }
    }
}
```

---

## 5. Key Android Engineering Best Practices

1. **Backpressure Handling**: Always use `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` combined with an `isBusy` flag to prevent frame queuing and keep real-time UI smooth at 30 FPS.
2. **Closing ImageProxy**: Always call `imageProxy.close()` in `addOnCompleteListener`. Forgetting to close `ImageProxy` will freeze the camera analyzer stream.
3. **Coordinate Scaling**: Calculate scale factors (`scaleX = screenWidth / imageWidth`, `scaleY = screenHeight / imageHeight`) taking 90°/270° sensor rotations into account so bounding boxes align precisely over camera objects.
