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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val samplePdf = copyAssetToCache("sample.pdf")

        if (samplePdf != null) {
            ActiveBook.fileName = samplePdf.name
            viewModel.openBook(samplePdf)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("SPLASH") }

                    when (currentScreen) {
                        "SPLASH" -> {
                            VideoSplashScreen(
                                onSplashFinished = {
                                    currentScreen = "LIBRARY"
                                }
                            )
                        }
                        "LIBRARY" -> {
                            LibraryScreen(
                                onBookSelected = { selectedFile ->
                                    // THE CRITICAL FIX: Set the unique file name before opening!
                                    ActiveBook.fileName = selectedFile.name
                                    viewModel.openBook(selectedFile)
                                    currentScreen = "BOOK"
                                }
                            )
                        }
                        "BOOK" -> {
                            BookScreen(
                                viewModel = viewModel,
                                onBackClicked = {
                                    currentScreen = "LIBRARY"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun copyAssetToCache(fileName: String): File? {
        return try {
            val cacheFile = File(cacheDir, fileName)
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