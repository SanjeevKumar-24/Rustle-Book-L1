package com.example.bookl1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // CRITICAL: This tells Android to actually build the Activity
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Copy a sample PDF from your assets folder
        val samplePdf = copyAssetToCache("sample.pdf")

        // 2. Tell the ViewModel to open that file
        if (samplePdf != null) {
            viewModel.openBook(samplePdf)
        }

        // 3. Launch the screen
        setContent {
            // Your theme block might be named differently (e.g., BookL1Theme)
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 1. Create a state to track if the splash is showing
                    var showSplash by remember { mutableStateOf(true) }

                    // 2. The Navigation Logic
                    if (showSplash) {
                        VideoSplashScreen(
                            onSplashFinished = {
                                showSplash = false // Hide splash, show book
                            }
                        )
                    } else {
                        // Load your amazing book engine!
                        BookScreen(viewModel)
                    }
                }
            }
        }
    }

    // Helper function to extract a file from the 'assets' folder for testing
    private fun copyAssetToCache(fileName: String): File? {
        return try {
            val cacheFile = File(cacheDir, fileName)
            // Only copy if we haven't done it already
            if (!cacheFile.exists()) {
                assets.open(fileName).use { inputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}