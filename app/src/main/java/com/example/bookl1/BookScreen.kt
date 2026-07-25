package com.example.bookl1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import kotlin.math.roundToInt

// --- 1. DATA MODELS & PERSISTENCE ---
enum class Tool { NONE, PEN, HIGHLIGHT, ERASER, NOTE, SCANNER }
data class DrawStroke(val points: List<Offset>, val tool: Tool)
data class StickyNoteData(val id: String, var position: Offset, var text: String)

enum class PageStyle(val label: String, val color: Color, val drawableRes: Int?) {
    PAPER("Old Paper", Color.Transparent, R.drawable.old_paper),
    TEXTURE_1("Cream", Color.Transparent, R.drawable.bg_texture_1),
    TEXTURE_2("Manuscript", Color.Transparent, R.drawable.bg_texture_2),
    TEXTURE_3("Parchment", Color.Transparent, R.drawable.bg_texture_3),
    WHITE("Clean", Color(0xFFFFFFFF), null),
    SEPIA("Warm", Color(0xFFF4ECD8), null),
    MINT("Zen Mint", Color(0xFFE8F5E9), null),
    SKY("Nordic Blue", Color(0xFFE1EBF0), null),
    CUSTOM("Custom Gallery", Color.Transparent, null)
}

data class SavedStroke(val xs: List<Float>, val ys: List<Float>, val tool: String) : Serializable
data class SavedNote(val id: String, val x: Float, val y: Float, val text: String) : Serializable
data class SavedData(
    val strokes: HashMap<Int, List<SavedStroke>>,
    val notes: HashMap<Int, List<SavedNote>>,
    val bookmarks: ArrayList<Int>
) : Serializable

object ActiveBook {
    var fileName: String = "default.pdf"
}

fun saveAnnotations(context: Context, strokes: Map<Int, List<DrawStroke>>, notes: Map<Int, List<StickyNoteData>>, bookmarks: List<Int>) {
    if (ActiveBook.fileName == "default.pdf") return

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
        val memoryFile = File(context.filesDir, "${ActiveBook.fileName}.dat")
        ObjectOutputStream(FileOutputStream(memoryFile)).use { it.writeObject(data) }
    } catch (e: Exception) { e.printStackTrace() }
}

fun loadAnnotations(context: Context): SavedData? {
    val buggySharedFile = File(context.filesDir, "default.pdf.dat")
    if (buggySharedFile.exists()) {
        buggySharedFile.delete()
    }

    if (ActiveBook.fileName == "default.pdf") return null

    val memoryFile = File(context.filesDir, "${ActiveBook.fileName}.dat")
    if (!memoryFile.exists()) return null
    return try {
        ObjectInputStream(FileInputStream(memoryFile)).use { it.readObject() as SavedData }
    } catch (e: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookScreen(viewModel: PdfViewModel, onBackClicked: () -> Unit) {
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

    // --- SETTINGS STATES ---
    var currentPageStyle by remember { mutableStateOf(PageStyle.TEXTURE_2) }
    var isRainPlaying by remember { mutableStateOf(false) }
    var rainVolume by remember { mutableFloatStateOf(0.5f) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var customBgUri by remember { mutableStateOf<Uri?>(null) }
    var customBgBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            customBgUri = it
            currentPageStyle = PageStyle.CUSTOM
        }
    }

    LaunchedEffect(customBgUri) {
        customBgUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        customBgBitmap = BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // --- HORIZONTAL DRAGGABLE TOOLBOX OFFSETS ---
    var toolboxOffsetX by remember { mutableFloatStateOf(0f) }
    var toolboxOffsetY by remember { mutableFloatStateOf(0f) }

    // --- OVERLAY STATES ---
    var showPageOverview by remember { mutableStateOf(false) }
    var showBookmarksList by remember { mutableStateOf(false) }

    val strokesMap = remember(ActiveBook.fileName) { mutableStateMapOf<Int, MutableList<DrawStroke>>() }
    val notesMap = remember(ActiveBook.fileName) { mutableStateMapOf<Int, MutableList<StickyNoteData>>() }
    val bookmarks = remember(ActiveBook.fileName) { mutableStateListOf<Int>() }

    // --- SCANNER STATES ---
    var scannerPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var scannedTextResult by remember { mutableStateOf("Scanning...") }

    // --- RAIN AUDIO ENGINE ---
    val rainPlayer = remember {
        try {
            MediaPlayer.create(context, R.raw.rain)?.apply {
                isLooping = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    LaunchedEffect(isRainPlaying, rainVolume) {
        try {
            if (isRainPlaying) {
                val scaledVolume = rainVolume * 0.45f
                rainPlayer?.setVolume(scaledVolume, scaledVolume)
                if (rainPlayer?.isPlaying != true) {
                    rainPlayer?.start()
                }
            } else {
                if (rainPlayer?.isPlaying == true) {
                    rainPlayer.pause()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (rainPlayer?.isPlaying == true) {
                    rainPlayer.stop()
                }
                rainPlayer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(ActiveBook.fileName) {
        strokesMap.clear()
        notesMap.clear()
        bookmarks.clear()
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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {

        // --- 1. THE PDF VIEWER ---
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
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
                                            // Empty string initial text so placeholder displays natively
                                            list.add(StickyNoteData(java.util.UUID.randomUUID().toString(), trueOffset, ""))
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
                            val bgBitmap = customBgBitmap
                            val resId = currentPageStyle.drawableRes

                            if (currentPageStyle == PageStyle.CUSTOM && bgBitmap != null) {
                                Image(bitmap = bgBitmap.asImageBitmap(), contentDescription = "Custom Background", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else if (resId != null) {
                                Image(painter = painterResource(id = resId), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(modifier = Modifier.fillMaxSize().background(currentPageStyle.color))
                            }

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

                            // --- DRAGGABLE & EDITABLE STICKY NOTES ---
                            notesMap[page]?.forEach { note ->
                                var showDialog by remember { mutableStateOf(false) }
                                var noteText by remember(note.id, note.text) { mutableStateOf(note.text) }
                                var notePosition by remember(note.id) { mutableStateOf(note.position) }

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(notePosition.x.toInt() - 30, notePosition.y.toInt() - 30) }
                                        .size(56.dp)
                                        .shadow(6.dp, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFFFF176)) // Post-it Yellow
                                        .border(1.dp, Color(0xFFFBC02D), RoundedCornerShape(12.dp))
                                        .pointerInput(note.id) {
                                            detectDragGestures(
                                                onDragEnd = {
                                                    note.position = notePosition
                                                    saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    notePosition = Offset(
                                                        notePosition.x + (dragAmount.x / scale),
                                                        notePosition.y + (dragAmount.y / scale)
                                                    )
                                                }
                                            )
                                        }
                                        .clickable { showDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Text("📝", fontSize = 20.sp)
                                        if (note.text.isNotBlank()) {
                                            Text(
                                                text = note.text,
                                                color = Color.Black,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                if (showDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDialog = false },
                                        title = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("📝", fontSize = 22.sp)
                                                Text("Sticky Note", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = noteText,
                                                    onValueChange = { noteText = it },
                                                    placeholder = { Text("Type your note here...", color = Color.Gray) },
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = Color(0xFFFFD700),
                                                        unfocusedBorderColor = Color(0x66FFFFFF),
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                        cursorColor = Color(0xFFFFD700)
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 100.dp),
                                                    maxLines = 5
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    note.text = noteText
                                                    saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                                    showDialog = false
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                                            ) {
                                                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        dismissButton = {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(
                                                    onClick = {
                                                        val list = notesMap[page] ?: mutableListOf()
                                                        list.removeAll { it.id == note.id }
                                                        notesMap[page] = list
                                                        saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                                        showDialog = false
                                                    },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                                                ) {
                                                    Text("Delete", fontWeight = FontWeight.Bold)
                                                }

                                                OutlinedButton(onClick = { showDialog = false }) {
                                                    Text("Cancel", color = Color.LightGray)
                                                }
                                            }
                                        },
                                        containerColor = Color(0xFF222226),
                                        shape = RoundedCornerShape(20.dp)
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

        // --- 2. TOP NAVIGATION BAR ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
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
                        Text(text = "⬅️", fontSize = 20.sp, modifier = Modifier.clickable { onBackClicked() })
                        Text(text = "🔲", fontSize = 20.sp, modifier = Modifier.clickable { showPageOverview = true })
                        Text(text = "📑", fontSize = 20.sp, modifier = Modifier.clickable { showBookmarksList = true })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(text = "🔍", fontSize = 20.sp, modifier = Modifier.clickable { })
                        Text(text = "⚙️", fontSize = 20.sp, modifier = Modifier.clickable { showSettingsDialog = true })
                        Text(text = "☰", fontSize = 20.sp, modifier = Modifier.clickable { isToolbarVisible = !isToolbarVisible })
                    }
                }
            }
        }

        // --- 3. THE HORIZONTAL DRAGGABLE TOOLBOX ---
        AnimatedVisibility(
            visible = isToolbarVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier
                        .offset { IntOffset(toolboxOffsetX.roundToInt(), toolboxOffsetY.roundToInt()) }
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF424242))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                toolboxOffsetX += dragAmount.x
                                toolboxOffsetY += dragAmount.y
                            }
                        }
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
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                if (isBookmarked) bookmarks.remove(pagerState.currentPage) else bookmarks.add(pagerState.currentPage)
                                saveAnnotations(context, strokesMap, notesMap, bookmarks)
                            },
                        contentAlignment = Alignment.Center
                    ) { Text(text = if (isBookmarked) "🔖" else "➕", fontSize = 20.sp) }
                }
            }
        }

        // --- 4. BOTTOM SLIDER OVERLAY ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
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

        // --- 5. PAGE OVERVIEW DIALOG ---
        if (showPageOverview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFA121212))
                    .zIndex(100f)
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Page Overview", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("❌", fontSize = 22.sp, modifier = Modifier.clickable { showPageOverview = false })
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
                    ) {
                        items(viewModel.pageCount) { pageIndex ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray)
                                    .clickable {
                                        scope.launch { pagerState.scrollToPage(pageIndex) }
                                        showPageOverview = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }
                                LaunchedEffect(pageIndex) { thumbBitmap = viewModel.getPageImage(pageIndex) }

                                if (thumbBitmap != null) {
                                    Image(bitmap = thumbBitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Box(modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xAA000000), RoundedCornerShape(topStart = 8.dp)).padding(8.dp)) {
                                    Text("Pg ${pageIndex + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                if (bookmarks.contains(pageIndex)) {
                                    Text("🔖", fontSize = 24.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 6. BOOKMARKS TELEPORT DIALOG ---
        if (showBookmarksList) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFA121212))
                    .zIndex(100f)
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Teleport to Bookmark", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("❌", fontSize = 22.sp, modifier = Modifier.clickable { showBookmarksList = false })
                    }

                    if (bookmarks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No bookmarks added yet.", color = Color.Gray, fontSize = 16.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
                        ) {
                            items(bookmarks.sorted()) { bookmarkedPage ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(0.7f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.DarkGray)
                                        .clickable {
                                            scope.launch { pagerState.scrollToPage(bookmarkedPage) }
                                            showBookmarksList = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }
                                    LaunchedEffect(bookmarkedPage) { thumbBitmap = viewModel.getPageImage(bookmarkedPage) }

                                    if (thumbBitmap != null) {
                                        Image(bitmap = thumbBitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }

                                    Box(modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xAA000000), RoundedCornerShape(topStart = 8.dp)).padding(8.dp)) {
                                        Text("Pg ${bookmarkedPage + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("🔖", fontSize = 28.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 7. MODERN PRO SETTINGS MODAL DASHBOARD ---
        if (showSettingsDialog) {
            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E1E22),
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reader Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A2A30))
                                    .clickable { showSettingsDialog = false },
                                contentAlignment = Alignment.Center
                            ) { Text("✕", fontSize = 14.sp, color = Color.LightGray) }
                        }

                        // Audio Card Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF2A2A30))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🌧️", fontSize = 24.sp)
                                    Column {
                                        Text("Ambient Rain", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("Soothing background audio", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                                Switch(
                                    checked = isRainPlaying,
                                    onCheckedChange = { isRainPlaying = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFFFD700),
                                        checkedTrackColor = Color(0xFF6200EE)
                                    )
                                )
                            }

                            AnimatedVisibility(visible = isRainPlaying) {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Volume", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${(rainVolume * 100).toInt()}%", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = rainVolume,
                                        onValueChange = { rainVolume = it },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFFFD700),
                                            activeTrackColor = Color(0xFF6200EE),
                                            inactiveTrackColor = Color(0xFF3E3E46)
                                        )
                                    )
                                }
                            }
                        }

                        // Page Texture Section
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("PAGE TEXTURE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                            // Custom Gallery Action Button
                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (currentPageStyle == PageStyle.CUSTOM) Color(0xFF6200EE) else Color(0xFF2A2A30)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("🖼️", fontSize = 18.sp)
                                        Text("Choose from Gallery...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (currentPageStyle == PageStyle.CUSTOM) {
                                        Text("✔ Active", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Interactive 2-Column Grid
                            val presets = PageStyle.values().filter { it != PageStyle.CUSTOM }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                presets.chunked(2).forEach { rowStyles ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        rowStyles.forEach { style ->
                                            val isSelected = currentPageStyle == style
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSelected) Color(0xFF383842) else Color(0xFF2A2A30))
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) Color(0xFFFFD700) else Color.Transparent,
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable { currentPageStyle = style }
                                                    .padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .clip(CircleShape)
                                                            .background(if (style.drawableRes != null) Color(0xFFD7CCC8) else style.color)
                                                            .border(1.dp, Color.Gray, CircleShape)
                                                    )
                                                    Text(
                                                        text = style.label,
                                                        color = if (isSelected) Color.White else Color.LightGray,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}