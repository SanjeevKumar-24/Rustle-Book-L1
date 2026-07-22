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

    // This is your main ViewModel. It holds the PDF data.
    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Copy a sample PDF from your assets folder
        val samplePdf = copyAssetToCache("sample.pdf")

        // 2. Tell the ViewModel to open that file
        if (samplePdf != null) {
            viewModel.openBook(samplePdf)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Track exactly which screen the user is currently looking at
                    var currentScreen by remember { mutableStateOf("SPLASH") }

                    // Navigation Logic
                    when (currentScreen) {
                        "SPLASH" -> {
                            VideoSplashScreen(
                                onSplashFinished = {
                                    // When the video ends, go to the Library
                                    currentScreen = "LIBRARY"
                                }
                            )
                        }
                        "LIBRARY" -> {
                            LibraryScreen(
                                onBookSelected = { selectedFile ->
                                    // Tell the PDF engine to load the exact file you tapped on!
                                    viewModel.openBook(selectedFile)
                                    // Switch over to the Book Reader screen
                                    currentScreen = "BOOK"
                                }
                            )
                        }
                        "BOOK" -> {
                            // Pass the class-level viewModel that has the actual PDF loaded inside it!
                            BookScreen(
                                viewModel = viewModel,
                                onBackClicked = {
                                    // When the ⬅️ button is tapped, go back to the Library
                                    currentScreen = "LIBRARY"
                                }
                            )
                        }
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