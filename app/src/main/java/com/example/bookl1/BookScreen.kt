package com.example.bookl1

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(viewModel: PdfViewModel) {
    val pagerState = rememberPagerState(pageCount = { viewModel.pageCount })
    val context = LocalContext.current

    // 1. THIS IS THE MAGIC FILE PICKER!
    // When a user picks a PDF, this copies it to the app and tells the ViewModel to open it.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            val tempFile = File(context.cacheDir, "my_real_book.pdf")
            context.contentResolver.openInputStream(selectedUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.openBook(tempFile)
        }
    }

    // The Scaffold is our wooden frame!
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BookL1", color = Color(0xFFFFD700)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF3E2723)
                ),
                // 2. THIS IS THE NEW BUTTON!
                actions = {
                    Button(
                        onClick = {
                            // Tell the file picker to only look for PDFs
                            filePickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037))
                    ) {
                        Text("Open Book", color = Color(0xFFFFD700))
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color(0xFF3E2723)
            ) {
                // Hide page count if there are 0 pages
                if (viewModel.pageCount > 0) {
                    Text(
                        text = "Page ${pagerState.currentPage + 1} of ${viewModel.pageCount}",
                        color = Color(0xFFFFD700),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    ) { paddingValues ->

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->

            var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(page) {
                pageBitmap = viewModel.getPageImage(page)
            }

            pageBitmap?.let { bitmap ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = pagerState.getOffsetDistanceInPages(page)
                            rotationY = pageOffset * -90f
                            transformOrigin = TransformOrigin(
                                pivotFractionX = if (pageOffset < 0) 1f else 0f,
                                pivotFractionY = 0.5f
                            )
                            alpha = 1f - abs(pageOffset).coerceIn(0f, 1f) * 0.5f
                        }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.old_paper),
                        contentDescription = "Old Paper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page $page",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                blendMode = BlendMode.Multiply
                            }
                    )
                }
            }
        }
    }
}