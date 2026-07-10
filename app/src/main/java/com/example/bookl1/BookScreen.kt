package com.example.bookl1

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.input.pointer.pointerInput
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

    // Zoom Memory
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Always reset the page to normal when a new page is turned
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BookL1", color = Color(0xFFFFD700)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF3E2723)
                ),
                actions = {
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)),
                        modifier = Modifier.padding(end = 8.dp)
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
                .padding(paddingValues),
            // Safety Lock: Only allow the pager to swipe if the scale is normal
            userScrollEnabled = scale <= 1.01f
        ) { page ->

            var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(page) {
                pageBitmap = viewModel.getPageImage(page)
            }

            pageBitmap?.let { bitmap ->

                // OUTER LAYER: The 3D Book Hinge
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

                    // INNER LAYER: The Custom Pointer Interceptor
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    do {
                                        val event = awaitPointerEvent()
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()

                                        // RULE 1: If 2 or more fingers are touching, it's a PINCH.
                                        if (event.changes.size >= 2) {
                                            scale = (scale * zoom).coerceIn(1f, 3f)
                                            offsetX += pan.x
                                            offsetY += pan.y
                                            // Tell Android: "I used this touch, don't pass it to the Pager"
                                            event.changes.forEach { it.consume() }
                                        }
                                        // RULE 2: If 1 finger is touching AND we are zoomed in, it's a DRAG.
                                        else if (scale > 1.01f) {
                                            offsetX += pan.x
                                            offsetY += pan.y
                                            // Tell Android: "I used this touch, don't pass it to the Pager"
                                            event.changes.forEach { it.consume() }
                                        }
                                        // RULE 3: If 1 finger and NOT zoomed in... DO NOTHING!
                                        // The touch passes right through the cracks to the HorizontalPager to flip the page.

                                    } while (event.changes.any { it.pressed })

                                    // When they let go, snap back to exactly 1.0 if they pinched out far enough
                                    if (scale <= 1.05f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
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
}