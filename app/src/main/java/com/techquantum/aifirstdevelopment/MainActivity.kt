package com.techquantum.aifirstdevelopment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.techquantum.aifirstdevelopment.ml.TextRecognizerScreen
import com.techquantum.aifirstdevelopment.theme.AIFirstDevelopmentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIFirstDevelopmentTheme {
                TextRecognizerScreen()
            }
        }
    }
}

