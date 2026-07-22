package com.example.bookl1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.*

// --- 1. DATA MODELS & PERSISTENCE ---
enum class Tool { NONE, PEN, HIGHLIGHT, ERASER, NOTE, SCANNER }
data class DrawStroke(val points: List<Offset>, val tool: Tool)
data class StickyNoteData(val id: String, val position: Offset, var text: String)

data class SavedStroke(val xs: List<Float>, val ys: List<Float>, val tool: String) : Serializable
data class SavedNote(val id: String, val x: Float, val y: Float, val text: String) : Serializable
data class SavedData(
    val strokes: HashMap<Int, List<SavedStroke>>,
    val notes: HashMap<Int, List<SavedNote>>,
    val bookmarks: ArrayList<Int>
) : Serializable

fun saveAnnotations(context: Context, strokes: Map<Int, List<DrawStroke>>, notes: Map<Int, List<StickyNoteData>>, bookmarks: List<Int>) {
    val sStrokes = HashMap<Int, List<SavedStroke>>()
    strokes.forEach { (page, list) ->
        sStrokes[page] = list.map { SavedStroke(it.points.map { p -> p.x }, it.points.map { p -> p.y }, it.tool.name) }
    }
    val sNotes = HashMap<Int, List<SavedNote>>()
    notes.forEach { (page, list) ->
        sNotes[page] = list.map { SavedNote(it.id, it.position.x, it.position.y, it.text) }
    }
    val data = SavedData(sStrokes, sNotes, ArrayList(bookmarks))
    try {
        ObjectOutputStream(FileOutputStream(File(context.filesDir, "book_memory.dat"))).use { it.writeObject(data) }
    } catch (e: Exception) { e.printStackTrace() }
}

fun loadAnnotations(context: Context): SavedData? {
    val file = File(context.filesDir, "book_memory.dat")
    if (!file.exists()) return null
    return try {
        ObjectInputStream(FileInputStream(file)).use { it.readObject() as SavedData }
    } catch (e: Exception) { null }
}

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

    // --- UI AND TOOL STATES ---
    var currentTool by remember { mutableStateOf(Tool.NONE) }
    var isToolbarVisible by remember { mutableStateOf(true) }

    val strokesMap = remember { mutableStateMapOf<Int, MutableList<DrawStroke>>() }
    val notesMap = remember { mutableStateMapOf<Int, MutableList<StickyNoteData>>() }
    val bookmarks = remember { mutableStateListOf<Int>() }

    // --- SCANNER STATES ---
    var scannerPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var scannedTextResult by remember { mutableStateOf("Scanning...") }

    LaunchedEffect(Unit) {
        loadAnnotations(context)?.let { saved ->
            saved.strokes.forEach { (page, sList) ->
                strokesMap[page] = sList.map { DrawStroke(it.xs.zip(it.ys) { x, y -> Offset(x, y) }, Tool.valueOf(it.tool)) }.toMutableList()
            }
            saved.notes.forEach { (page, sList) ->
                notesMap[page] = sList.map { StickyNoteData(it.id, Offset(it.x, it.y), it.text) }.toMutableList()
            }
            bookmarks.addAll(saved.bookmarks)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f; offsetX = 0f; offsetY = 0f
        if (!isFirstLoad) {
            try {
                val player = MediaPlayer.create(context, R.raw.page_flip)
                player.start()
                player.setOnCompletionListener { it.release() }
            } catch (e: Exception) { e.printStackTrace() }
        } else isFirstLoad = false
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color(0xFF2C2C2C))) {
                Spacer(modifier = Modifier.statusBarsPadding())

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(text = "⬅️", fontSize = 20.sp, modifier = Modifier.clickable { (context as? Activity)?.finish() })
                        Text(text = "🔲", fontSize = 20.sp, modifier = Modifier.clickable { })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(text = "🔍", fontSize = 20.sp, modifier = Modifier.clickable { })
                        Text(text = "⚙️", fontSize = 20.sp, modifier = Modifier.clickable { })
                        Text(text = "☰", fontSize = 20.sp, modifier = Modifier.clickable { isToolbarVisible = !isToolbarVisible })
                    }
                }

                AnimatedVisibility(visible = isToolbarVisible) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF424242))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tools = listOf(Tool.NONE to "✋", Tool.PEN to "🖊️", Tool.HIGHLIGHT to "🖍️", Tool.ERASER to "🧽", Tool.NOTE to "📝", Tool.SCANNER to "⭕")

                            tools.forEach { (t, emoji) ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (currentTool == t) Color(0xFF6200EE) else Color.Transparent, CircleShape)
                                        .clickable {
                                            currentTool = t
                                            scannerPoints = emptyList()
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Text(text = emoji, fontSize = 20.sp) }
                            }

                            Box(modifier = Modifier.height(20.dp).width(1.dp).background(Color.Gray))

                            val isBookmarked = bookmarks.contains(pagerState.currentPage)
                            Box(
                                modifier = Modifier.size(36.dp).clickable {
                                    if (isBookmarked) bookmarks.remove(pagerState.currentPage) else bookmarks.add(pagerState.currentPage)
                                    saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                },
                                contentAlignment = Alignment.Center
                            ) { Text(text = if (isBookmarked) "🔖" else "📑", fontSize = 20.sp) }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2C))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${if (viewModel.pageCount > 0) viewModel.pageCount else 0}",
                        color = Color(0xFFE0E0E0),
                        fontSize = 14.sp
                    )

                    Slider(
                        value = pagerState.currentPage.toFloat(),
                        onValueChange = { scope.launch { pagerState.scrollToPage(it.toInt()) } },
                        valueRange = 0f..(if (viewModel.pageCount > 0) (viewModel.pageCount - 1).toFloat() else 0f),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFE0E0E0), activeTrackColor = Color(0xFFE0E0E0)),
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                }
            }
        }
    ) { paddingValues ->

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            userScrollEnabled = scale <= 1.01f && currentTool == Tool.NONE
        ) { page ->

            var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(page) { pageBitmap = viewModel.getPageImage(page) }

            pageBitmap?.let { bitmap ->
                Box(
                    modifier = Modifier.fillMaxSize()
                        .zIndex(if (pagerState.getOffsetDistanceInPages(page) < 0f) 1f else 0f)
                        .graphicsLayer {
                            val pageOffset = pagerState.getOffsetDistanceInPages(page)
                            cameraDistance = 30f
                            if (pageOffset < 0f) {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = pageOffset * 90f
                            } else {
                                translationX = pageOffset * -size.width
                                rotationY = 0f
                                alpha = 0.5f + (1f - pageOffset) * 0.5f
                            }
                        }
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val screenWidth = constraints.maxWidth.toFloat()
                        val screenHeight = constraints.maxHeight.toFloat()
                        var currentLiveStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

                        // --- THE FIX: Un-scaling Math ---
                        // This dynamically calculates exactly where the ink belongs on the true PDF
                        // regardless of how far you are zoomed in or panned away.
                        val unscaleOffset = { screenOffset: Offset ->
                            val pivotX = screenWidth / 2f
                            val pivotY = screenHeight / 2f
                            val trueX = (screenOffset.x - offsetX - pivotX) / scale + pivotX
                            val trueY = (screenOffset.y - offsetY - pivotY) / scale + pivotY
                            Offset(trueX, trueY)
                        }

                        val eraseAt: (Offset) -> Unit = { trueTouchPoint ->
                            val currentStrokes = strokesMap[page] ?: mutableListOf()
                            val newStrokes = currentStrokes.filter { stroke ->
                                stroke.points.none { p ->
                                    val dx = p.x - trueTouchPoint.x
                                    val dy = p.y - trueTouchPoint.y
                                    // Scale the eraser radius dynamically so it works while zoomed
                                    (dx * dx + dy * dy) < (2500f / (scale * scale))
                                }
                            }
                            if (newStrokes.size != currentStrokes.size) {
                                strokesMap[page] = newStrokes.toMutableList()
                                saveAnnotations(context, strokesMap, notesMap, bookmarks)
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxSize()
                                .pointerInput(currentTool) {
                                    if (currentTool == Tool.SCANNER) {
                                        detectDragGestures(
                                            onDragStart = { offset -> scannerPoints = listOf(unscaleOffset(offset)) },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                scannerPoints = scannerPoints + unscaleOffset(change.position)
                                            },
                                            onDragEnd = {
                                                if (scannerPoints.size > 10) {
                                                    scannedTextResult = "Scanning text..."
                                                    showScannerDialog = true

                                                    val minX = scannerPoints.minOf { it.x }
                                                    val maxX = scannerPoints.maxOf { it.x }
                                                    val minY = scannerPoints.minOf { it.y }
                                                    val maxY = scannerPoints.maxOf { it.y }

                                                    val imageScale = minOf(screenWidth / bitmap.width, screenHeight / bitmap.height)
                                                    val imgOffsetX = (screenWidth - (bitmap.width * imageScale)) / 2f
                                                    val imgOffsetY = (screenHeight - (bitmap.height * imageScale)) / 2f

                                                    val bMinX = ((minX - imgOffsetX) / imageScale).toInt()
                                                    val bMaxX = ((maxX - imgOffsetX) / imageScale).toInt()
                                                    val bMinY = ((minY - imgOffsetY) / imageScale).toInt()
                                                    val bMaxY = ((maxY - imgOffsetY) / imageScale).toInt()

                                                    val image = InputImage.fromBitmap(bitmap, 0)
                                                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                                                    recognizer.process(image)
                                                        .addOnSuccessListener { visionText ->
                                                            var extracted = ""
                                                            for (block in visionText.textBlocks) {
                                                                val box = block.boundingBox
                                                                if (box != null && box.right >= bMinX && box.left <= bMaxX && box.bottom >= bMinY && box.top <= bMaxY) {
                                                                    extracted += block.text + " "
                                                                }
                                                            }
                                                            scannedTextResult = extracted.trim().ifEmpty { "No text found inside the circle." }
                                                        }
                                                        .addOnFailureListener { e ->
                                                            scannedTextResult = "Error scanning text."
                                                        }

                                                } else {
                                                    scannerPoints = emptyList()
                                                }
                                            }
                                        )
                                    }
                                    else if (currentTool == Tool.PEN || currentTool == Tool.HIGHLIGHT || currentTool == Tool.ERASER) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val trueOffset = unscaleOffset(offset)
                                                if (currentTool == Tool.ERASER) eraseAt(trueOffset)
                                                else currentLiveStroke = listOf(trueOffset)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val trueOffset = unscaleOffset(change.position)
                                                if (currentTool == Tool.ERASER) eraseAt(trueOffset)
                                                else currentLiveStroke = currentLiveStroke + trueOffset
                                            },
                                            onDragEnd = {
                                                if (currentLiveStroke.isNotEmpty() && currentTool != Tool.ERASER) {
                                                    val list = strokesMap[page] ?: mutableListOf()
                                                    list.add(DrawStroke(currentLiveStroke, currentTool))
                                                    strokesMap[page] = list
                                                    currentLiveStroke = emptyList()
                                                    saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                                }
                                            }
                                        )
                                    }
                                    else if (currentTool == Tool.NOTE) {
                                        detectTapGestures(onTap = { offset ->
                                            val trueOffset = unscaleOffset(offset)
                                            val list = notesMap[page] ?: mutableListOf()
                                            list.add(StickyNoteData(java.util.UUID.randomUUID().toString(), trueOffset, "Type here..."))
                                            notesMap[page] = list
                                            currentTool = Tool.NONE
                                            saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                        })
                                    }
                                }
                                .pointerInput(currentTool) {
                                    if (currentTool == Tool.NONE) {
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
                                                } else if (scale > 1.01f) {
                                                    val maxX = (screenWidth * (scale - 1)) / 2f
                                                    val maxY = (screenHeight * (scale - 1)) / 2f
                                                    offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                                    offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                                }
                                            } while (event.changes.any { it.pressed })
                                            if (scale <= 1.05f) { scale = 1f; offsetX = 0f; offsetY = 0f }
                                        }
                                    }
                                }
                                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
                        ) {
                            Image(painter = painterResource(id = R.drawable.old_paper), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer { blendMode = BlendMode.Multiply })

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val allPaths = (strokesMap[page] ?: emptyList()) + if (currentLiveStroke.isNotEmpty()) listOf(DrawStroke(currentLiveStroke, currentTool)) else emptyList()
                                for (stroke in allPaths) {
                                    if (stroke.points.size < 2) continue
                                    val path = Path().apply {
                                        moveTo(stroke.points.first().x, stroke.points.first().y)
                                        for (i in 1 until stroke.points.size) lineTo(stroke.points[i].x, stroke.points[i].y)
                                    }
                                    val color = if (stroke.tool == Tool.HIGHLIGHT) Color.Yellow.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.8f)
                                    val width = if (stroke.tool == Tool.HIGHLIGHT) 45f else 8f
                                    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }

                                if (currentTool == Tool.SCANNER && scannerPoints.isNotEmpty()) {
                                    val scannerPath = Path().apply {
                                        moveTo(scannerPoints.first().x, scannerPoints.first().y)
                                        for (i in 1 until scannerPoints.size) lineTo(scannerPoints[i].x, scannerPoints[i].y)
                                        lineTo(scannerPoints.first().x, scannerPoints.first().y)
                                    }
                                    drawPath(path = scannerPath, color = Color(0xFF00E5FF), style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f))))
                                    drawPath(path = scannerPath, color = Color(0xFF00E5FF).copy(alpha = 0.2f))
                                }
                            }

                            if (showScannerDialog) {
                                AlertDialog(
                                    onDismissRequest = {
                                        showScannerDialog = false
                                        scannerPoints = emptyList()
                                    },
                                    title = { Text("Scanned Text") },
                                    text = { Text(scannedTextResult) },
                                    confirmButton = {
                                        Button(onClick = {
                                            showScannerDialog = false
                                            scannerPoints = emptyList()
                                            currentTool = Tool.NONE
                                        }) { Text("Close") }
                                    },
                                    dismissButton = {
                                        if (scannedTextResult != "Scanning..." && !scannedTextResult.contains("No text found")) {
                                            OutlinedButton(onClick = {
                                                val url = "https://translate.google.com/?sl=auto&tl=en&text=${Uri.encode(scannedTextResult)}&op=translate"
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                showScannerDialog = false
                                                scannerPoints = emptyList()
                                                currentTool = Tool.NONE
                                            }) { Text("Translate") }
                                        }
                                    }
                                )
                            }

                            notesMap[page]?.forEach { note ->
                                var showDialog by remember { mutableStateOf(false) }
                                var noteText by remember { mutableStateOf(note.text) }

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(note.position.x.toInt() - 30, note.position.y.toInt() - 30) }
                                        .size(60.dp)
                                        .clickable { showDialog = true },
                                    contentAlignment = Alignment.Center
                                ) { Text("📝", fontSize = 32.sp) }

                                if (showDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDialog = false },
                                        title = { Text("Sticky Note") },
                                        text = { OutlinedTextField(value = noteText, onValueChange = { noteText = it; note.text = it }) },
                                        confirmButton = {
                                            Button(onClick = {
                                                showDialog = false
                                                saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                            }) { Text("Save") }
                                        }
                                    )
                                }
                            }

                            if (bookmarks.contains(page)) {
                                Text("🔖", fontSize = 45.sp, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}