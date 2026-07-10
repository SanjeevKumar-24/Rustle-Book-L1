package com.example.bookl1

import android.graphics.Bitmap
import android.media.MediaPlayer
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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(viewModel: PdfViewModel) {
    val pagerState = rememberPagerState(pageCount = { viewModel.pageCount })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var isFirstLoad by remember { mutableStateOf(true) }

    // Page turn resets zoom AND plays the sound effect
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f

        if (!isFirstLoad) {
            try {
                val mediaPlayer = MediaPlayer.create(context, R.raw.page_flip)
                mediaPlayer.start()
                mediaPlayer.setOnCompletionListener { it.release() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            isFirstLoad = false
        }
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
            Surface(color = Color(0xFF3E2723), tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Slider(
                        value = pagerState.currentPage.toFloat(),
                        onValueChange = {
                            scope.launch { pagerState.scrollToPage(it.toInt()) }
                        },
                        valueRange = 0f..(if (viewModel.pageCount > 0) (viewModel.pageCount - 1).toFloat() else 0f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFD700),
                            activeTrackColor = Color(0xFFFFD700)
                        )
                    )
                    Text(
                        text = "Page ${pagerState.currentPage + 1} of ${viewModel.pageCount}",
                        color = Color(0xFFFFD700),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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
            userScrollEnabled = scale <= 1.01f
        ) { page ->

            var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(page) {
                pageBitmap = viewModel.getPageImage(page)
            }

            pageBitmap?.let { bitmap ->

                // OUTER BOX: The Realistic Flip Animation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 1. Force the turning page to draw ON TOP of the next page
                        .zIndex(if (pagerState.getOffsetDistanceInPages(page) < 0f) 1f else 0f)
                        .graphicsLayer {
                            val pageOffset = pagerState.getOffsetDistanceInPages(page)

                            // 2. Set camera distance to prevent a distorted "fisheye" look
                            cameraDistance = 30f

                            if (pageOffset < 0f) {
                                // THE LEFT PAGE (Turning over)
                                // Hinge on the left spine and swing left
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = pageOffset * 90f
                            } else {
                                // THE NEXT PAGE (Sitting underneath)
                                // Cancel the pager's default scroll so it sits perfectly still!
                                translationX = pageOffset * -size.width
                                rotationY = 0f
                                // Add a shadow cast by the turning page that brightens as the page opens
                                alpha = 0.5f + (1f - pageOffset) * 0.5f
                            }
                        }
                ) {

                    // INNER BOX: Your custom gestures (100% unchanged)
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val screenWidth = constraints.maxWidth.toFloat()
                        val screenHeight = constraints.maxHeight.toFloat()

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

                                            if (event.changes.size >= 2) {
                                                scale = (scale * zoom).coerceIn(1f, 3f)
                                                val maxX = (screenWidth * (scale - 1)) / 2f
                                                val maxY = (screenHeight * (scale - 1)) / 2f
                                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                            }
                                            else if (scale > 1.01f) {
                                                val maxX = (screenWidth * (scale - 1)) / 2f
                                                val maxY = (screenHeight * (scale - 1)) / 2f
                                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                            }

                                        } while (event.changes.any { it.pressed })

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
}