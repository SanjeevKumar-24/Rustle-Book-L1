package com.example.bookl1

import android.content.Intent
import android.net.Uri
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

        // 1. Check if the app was launched from the "Open With" or "Share" drawer
        val launchedUri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }

        // 2. If a URI exists, copy it to cache and prepare to open it directly!
        var initialScreen = "SPLASH"
        if (launchedUri != null) {
            val fileFromUri = copyUriToCache(launchedUri)
            if (fileFromUri != null) {
                ActiveBook.fileName = fileFromUri.name
                viewModel.openBook(fileFromUri)
                initialScreen = "BOOK" // Skip splash and go straight to reading!
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Start on "BOOK" if opened via drawer, otherwise start on "SPLASH"
                    var currentScreen by remember { mutableStateOf(initialScreen) }

                    when (currentScreen) {
                        "SPLASH" -> {
                            SplashScreen(
                                onTimeout = {
                                    currentScreen = "LIBRARY"
                                }
                            )
                        }
                        "LIBRARY" -> {
                            LibraryScreen(
                                onBookSelected = { selectedFile ->
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

    // Helper: Converts a shared WhatsApp/Drive content:// URI into a readable local file
    private fun copyUriToCache(uri: Uri): File? {
        return try {
            // Get original file name or fall back to timestamp
            var fileName = "shared_${System.currentTimeMillis()}.pdf"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            val cacheFile = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}