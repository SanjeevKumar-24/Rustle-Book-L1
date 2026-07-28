package com.example.bookl1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Check if the app was launched from the "Open With" or "Share" system drawer
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

        // 2. If launched with a PDF URI, start on "LOADING" screen. Otherwise, start on "SPLASH".
        val initialScreen = if (launchedUri != null) "LOADING" else "SPLASH"

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(initialScreen) }

                    // 3. Trigger the background import when starting on the LOADING screen
                    LaunchedEffect(launchedUri) {
                        if (launchedUri != null && currentScreen == "LOADING") {
                            viewModel.importAndOpenUri(this@MainActivity, launchedUri) { success ->
                                if (success) {
                                    currentScreen = "BOOK"
                                } else {
                                    Toast.makeText(this@MainActivity, "Failed to open PDF file", Toast.LENGTH_SHORT).show()
                                    currentScreen = "LIBRARY"
                                }
                            }
                        }
                    }

                    when (currentScreen) {
                        "SPLASH" -> {
                            SplashScreen(
                                onTimeout = {
                                    currentScreen = "LIBRARY"
                                }
                            )
                        }
                        "LOADING" -> {
                            // Branded Rustle Loading Screen for massive PDFs
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ThemeDarkBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = ThemeGold,
                                        trackColor = ThemeCardBg,
                                        modifier = Modifier.size(56.dp),
                                        strokeWidth = 5.dp
                                    )
                                    Text(
                                        text = "Importing Book...",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Preparing high-resolution pages",
                                        color = Color(0xFFA9B7C6),
                                        fontSize = 14.sp
                                    )
                                }
                            }
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
}