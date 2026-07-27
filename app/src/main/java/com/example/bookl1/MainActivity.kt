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

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("SPLASH") }

                    when (currentScreen) {
                        "SPLASH" -> {
                            // FIXED: Matches fun SplashScreen(onTimeout = { ... })
                            SplashScreen(
                                onTimeout = {
                                    currentScreen = "LIBRARY"
                                }
                            )
                        }
                        "LIBRARY" -> {
                            LibraryScreen(
                                onBookSelected = { selectedFile ->
                                    // Set the unique file name before opening the book
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
}