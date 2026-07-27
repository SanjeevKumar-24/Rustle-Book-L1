package com.example.bookl1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// --- 1. RUSTLE / RUSTK THEME PALETTE ---
val ThemeDarkBg = Color(0xFF121212)     // Deep Charcoal / Mandala Black
val ThemeCardBg = Color(0xFF1E1E1E)     // Sleek Dark Card & Toolbar BG
val ThemeAccentBlue = Color(0xFF2C2C2C) // Secondary Button & Item Accent
val ThemeGold = Color(0xFFD4AF37)       // Rustle Brand Gold
val ThemeGoldLight = Color(0xFFF3E5AB)  // Soft Parchment Highlight

// --- 2. DATA MODELS & PERSISTENCE ---
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
    var fileName: String = ""
}

// Helper to ignore sample/default files
private fun isDefaultOrSampleFile(fileName: String): Boolean {
    val cleanName = fileName.trim().lowercase()
    return cleanName.isEmpty() || cleanName == "default.pdf" || cleanName == "sample.pdf"
}

fun saveAnnotations(context: Context, strokes: Map<Int, List<DrawStroke>>, notes: Map<Int, List<StickyNoteData>>, bookmarks: List<Int>) {
    if (isDefaultOrSampleFile(ActiveBook.fileName)) return

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
    val buggySampleFile = File(context.filesDir, "sample.pdf.dat")
    if (buggySampleFile.exists()) {
        buggySampleFile.delete()
    }

    if (isDefaultOrSampleFile(ActiveBook.fileName)) return null

    val memoryFile = File(context.filesDir, "${ActiveBook.fileName}.dat")
    if (!memoryFile.exists()) return null
    return try {
        ObjectInputStream(FileInputStream(memoryFile)).use { it.readObject() as SavedData }
    } catch (e: Exception) { null }
}

// Helper: Async background OCR scanner for searching and Text-to-Speech
suspend fun scanBitmapForText(bitmap: Bitmap): String = suspendCoroutine { cont ->
    try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resume("") }
    } catch (e: Exception) {
        cont.resume("")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookScreen(viewModel: PdfViewModel, onBackClicked: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { viewModel.pageCount })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val configuration = LocalConfiguration.current

    // --- TABLET & SCREEN SIZE DETECTION ---
    val isTablet = remember(configuration) {
        configuration.smallestScreenWidthDp >= 600 ||
                (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }
    val gridColumns = if (isTablet) 4 else 2
    val dialogMaxWidth = if (isTablet) 600.dp else 400.dp
    val toolbarIconSize = if (isTablet) 44.dp else 36.dp
    val toolbarIconFontSize = if (isTablet) 24.sp else 20.sp

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

    // --- SEARCH STATES ---
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchProgress by remember { mutableFloatStateOf(0f) }
    var searchCurrentPage by remember { mutableIntStateOf(0) }
    val searchResults = remember { mutableStateListOf<Pair<Int, String>>() }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // --- TEXT TO SPEECH (TTS) STATES & ENGINE ---
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isReadingLoading by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { isSpeaking = true }
            override fun onDone(utteranceId: String?) { isSpeaking = false }
            override fun onError(utteranceId: String?) { isSpeaking = false }
        })
        tts = engine
        onDispose {
            try {
                engine.stop()
                engine.shutdown()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val toggleReadAloud = {
        if (isSpeaking || isReadingLoading) {
            tts?.stop()
            isSpeaking = false
            isReadingLoading = false
        } else {
            scope.launch {
                isReadingLoading = true
                val bmp = viewModel.getPageImage(pagerState.currentPage)
                if (bmp != null) {
                    val text = scanBitmapForText(bmp)
                    isReadingLoading = false
                    if (text.isNotBlank()) {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PAGE_READ_${pagerState.currentPage}")
                        isSpeaking = true
                    }
                } else {
                    isReadingLoading = false
                }
            }
        }
    }

    val strokesMap = remember(ActiveBook.fileName) { mutableStateMapOf<Int, MutableList<DrawStroke>>() }
    val notesMap = remember(ActiveBook.fileName) { mutableStateMapOf<Int, MutableList<StickyNoteData>>() }
    val bookmarks = remember(ActiveBook.fileName) { mutableStateListOf<Int>() }

    // --- SCANNER STATES ---
    var scannerPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var scannedTextResult by remember { mutableStateOf("Scanning...") }

    // --- AUDIO ENGINES ---
    val rainPlayer = remember {
        try {
            MediaPlayer.create(context, R.raw.rain)?.apply { isLooping = true }
        } catch (e: Exception) { null }
    }

    val pageFlipPlayer = remember {
        try {
            MediaPlayer.create(context, R.raw.page_flip)
        } catch (e: Exception) { null }
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
        } catch (e: Exception) { e.printStackTrace() }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (rainPlayer?.isPlaying == true) rainPlayer.stop()
                rainPlayer?.release()
                if (pageFlipPlayer?.isPlaying == true) pageFlipPlayer.stop()
                pageFlipPlayer?.release()
            } catch (e: Exception) { e.printStackTrace() }
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
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
        }
        if (!isFirstLoad) {
            try {
                if (pageFlipPlayer?.isPlaying == true) {
                    pageFlipPlayer.pause()
                }
                pageFlipPlayer?.seekTo(0)
                pageFlipPlayer?.start()
            } catch (e: Exception) { e.printStackTrace() }
        } else isFirstLoad = false
    }

    Box(modifier = Modifier.fillMaxSize().background(ThemeDarkBg)) {

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
                                    drawPath(path = scannerPath, color = ThemeGold, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f))))
                                    drawPath(path = scannerPath, color = ThemeGold.copy(alpha = 0.2f))
                                }
                            }

                            // --- SCANNED TEXT MODAL WITH COPY BUTTON & RUSTLE THEME ---
                            if (showScannerDialog) {
                                AlertDialog(
                                    onDismissRequest = {
                                        showScannerDialog = false
                                        scannerPoints = emptyList()
                                    },
                                    modifier = Modifier.widthIn(max = dialogMaxWidth),
                                    containerColor = ThemeCardBg,
                                    titleContentColor = Color.White,
                                    textContentColor = Color(0xFFA9B7C6),
                                    shape = RoundedCornerShape(24.dp),
                                    title = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("⭕", fontSize = 22.sp)
                                            Text("Scanned Text", fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    text = {
                                        SelectionContainer {
                                            Text(
                                                text = scannedTextResult,
                                                fontSize = 15.sp,
                                                lineHeight = 22.sp,
                                                color = Color.White
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                showScannerDialog = false
                                                scannerPoints = emptyList()
                                                currentTool = Tool.NONE
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ThemeGold)
                                        ) {
                                            Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        if (scannedTextResult != "Scanning..." && !scannedTextResult.contains("No text found")) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(scannedTextResult))
                                                        Toast.makeText(context, "Text copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeGold),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, ThemeGold)
                                                ) {
                                                    Text("Copy 📋", fontWeight = FontWeight.Bold)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        val url = "https://translate.google.com/?sl=auto&tl=en&text=${Uri.encode(scannedTextResult)}&op=translate"
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                        showScannerDialog = false
                                                        scannerPoints = emptyList()
                                                        currentTool = Tool.NONE
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeGoldLight),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, ThemeGoldLight)
                                                ) {
                                                    Text("Translate 🌐", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                )
                            }

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
                                        .background(ThemeGoldLight)
                                        .border(1.dp, ThemeGold, RoundedCornerShape(12.dp))
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
                                        modifier = Modifier.widthIn(max = dialogMaxWidth),
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
                                                        focusedBorderColor = ThemeGold,
                                                        unfocusedBorderColor = Color(0x66FFFFFF),
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                        cursorColor = ThemeGold
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
                                                colors = ButtonDefaults.buttonColors(containerColor = ThemeGold)
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
                                        containerColor = ThemeCardBg,
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

        // --- 2. TOP NAVIGATION BAR WITH AUDIO BANNER ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Column(modifier = Modifier.background(ThemeCardBg)) {
                Spacer(modifier = Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isTablet) 32.dp else 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // REDUCED SPACING: Changed from 24.dp to 16.dp on phones so all icons fit without clipping!
                    Row(horizontalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 16.dp)) {
                        Text(text = "⬅️", fontSize = toolbarIconFontSize, color = Color.White, modifier = Modifier.clickable { onBackClicked() })
                        Text(text = "🔲", fontSize = toolbarIconFontSize, color = Color.White, modifier = Modifier.clickable { showPageOverview = true })
                        Text(text = "📑", fontSize = toolbarIconFontSize, color = Color.White, modifier = Modifier.clickable { showBookmarksList = true })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 16.dp)) {
                        Text(
                            text = if (isReadingLoading) "⏳" else if (isSpeaking) "⏹️" else "🔊",
                            fontSize = toolbarIconFontSize,
                            color = Color.White,
                            modifier = Modifier.clickable { toggleReadAloud() }
                        )
                        Text(text = "🔍", fontSize = toolbarIconFontSize, color = Color.White, modifier = Modifier.clickable { showSearchDialog = true })
                        Text(text = "⚙️", fontSize = toolbarIconFontSize, color = Color.White, modifier = Modifier.clickable { showSettingsDialog = true })
                        // CHANGED TO TOOLBOX EMOJI (🧰) AND ADDED EXPLICIT WHITE COLOR:
                        Text(text = "🧰", fontSize = toolbarIconFontSize, color = Color.White, modifier = Modifier.clickable { isToolbarVisible = !isToolbarVisible })
                    }
                }

                AnimatedVisibility(visible = isSpeaking || isReadingLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ThemeAccentBlue)
                            .padding(horizontal = if (isTablet) 32.dp else 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isReadingLoading) "⏳" else "🔊", fontSize = 16.sp)
                            Text(
                                text = if (isReadingLoading) "Extracting text from page..." else "Reading Page ${pagerState.currentPage + 1}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Stop ⏹️",
                            color = ThemeGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { toggleReadAloud() }
                        )
                    }
                }
            }
        }

        // --- 3. THE HORIZONTAL DRAGGABLE TOOLBOX ---
        AnimatedVisibility(
            visible = isToolbarVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isTablet) 90.dp else 72.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier
                        .offset { IntOffset(toolboxOffsetX.roundToInt(), toolboxOffsetY.roundToInt()) }
                        .clip(RoundedCornerShape(50))
                        .background(ThemeCardBg)
                        .border(1.dp, ThemeGold, RoundedCornerShape(50))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                toolboxOffsetX += dragAmount.x
                                toolboxOffsetY += dragAmount.y
                            }
                        }
                        .padding(horizontal = if (isTablet) 20.dp else 12.dp, vertical = if (isTablet) 10.dp else 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tools = listOf(Tool.NONE to "✋", Tool.PEN to "🖊️", Tool.HIGHLIGHT to "🖍️", Tool.ERASER to "🧽", Tool.NOTE to "📝", Tool.SCANNER to "⭕")

                    tools.forEach { (t, emoji) ->
                        Box(
                            modifier = Modifier
                                .size(toolbarIconSize)
                                .background(if (currentTool == t) ThemeGold.copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                                .clickable {
                                    currentTool = t
                                    scannerPoints = emptyList()
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(text = emoji, fontSize = toolbarIconFontSize) }
                    }

                    Box(modifier = Modifier.height(20.dp).width(1.dp).background(Color.Gray))

                    val isBookmarked = bookmarks.contains(pagerState.currentPage)
                    Box(
                        modifier = Modifier
                            .size(toolbarIconSize)
                            .clickable {
                                if (isBookmarked) bookmarks.remove(pagerState.currentPage) else bookmarks.add(pagerState.currentPage)
                                saveAnnotations(context, strokesMap, notesMap, bookmarks)
                            },
                        contentAlignment = Alignment.Center
                    ) { Text(text = if (isBookmarked) "🔖" else "➕", fontSize = toolbarIconFontSize) }
                }
            }
        }

        // --- 4. BOTTOM SLIDER OVERLAY ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(ThemeCardBg)
                .navigationBarsPadding()
                .padding(horizontal = if (isTablet) 32.dp else 16.dp, vertical = 6.dp)
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
                    colors = SliderDefaults.colors(
                        thumbColor = ThemeGold,
                        activeTrackColor = ThemeGoldLight,
                        inactiveTrackColor = ThemeDarkBg
                    ),
                    modifier = Modifier.weight(1f).height(24.dp)
                )
            }
        }

        // --- 5. PAGE OVERVIEW DIALOG ---
        if (showPageOverview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ThemeDarkBg.copy(alpha = 0.96f))
                    .zIndex(100f)
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = if (isTablet) 48.dp else 16.dp).statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Page Overview", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("❌", fontSize = 22.sp, modifier = Modifier.clickable { showPageOverview = false })
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
                    ) {
                        items(viewModel.pageCount) { pageIndex ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ThemeCardBg)
                                    .border(1.dp, ThemeGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
                    .background(ThemeDarkBg.copy(alpha = 0.96f))
                    .zIndex(100f)
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = if (isTablet) 48.dp else 16.dp).statusBarsPadding()) {
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
                            columns = GridCells.Fixed(gridColumns),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
                        ) {
                            items(bookmarks.sorted()) { bookmarkedPage ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(0.7f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ThemeCardBg)
                                        .border(1.dp, ThemeGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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

        // --- 7. FULLSCREEN BOOK SEARCH DIALOG ---
        if (showSearchDialog) {
            val runSearch = {
                keyboardController?.hide()
                searchJob?.cancel()
                searchResults.clear()
                val queryLower = searchQuery.trim().lowercase()

                if (queryLower.isNotEmpty()) {
                    searchJob = scope.launch {
                        isSearching = true
                        val totalPages = viewModel.pageCount

                        for (p in 0 until totalPages) {
                            if (!isSearching) break
                            searchCurrentPage = p
                            searchProgress = (p + 1).toFloat() / if (totalPages > 0) totalPages else 1

                            val bmp = viewModel.getPageImage(p)
                            if (bmp != null) {
                                val text = scanBitmapForText(bmp)
                                val textLower = text.lowercase()
                                val matchIndex = textLower.indexOf(queryLower)
                                if (matchIndex != -1) {
                                    val start = maxOf(0, matchIndex - 35)
                                    val end = minOf(text.length, matchIndex + queryLower.length + 65)
                                    val snippet = "..." + text.substring(start, end).replace("\n", " ") + "..."
                                    searchResults.add(p to snippet)
                                }
                            }
                            delay(15)
                        }
                        isSearching = false
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ThemeDarkBg.copy(alpha = 0.96f))
                    .zIndex(100f)
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isTablet) 48.dp else 16.dp)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔍 Search in Book", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "❌",
                            fontSize = 22.sp,
                            modifier = Modifier.clickable {
                                isSearching = false
                                searchJob?.cancel()
                                showSearchDialog = false
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Enter keyword (e.g. money, habit)...", color = Color.Gray, fontSize = 14.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ThemeGold,
                                unfocusedBorderColor = ThemeAccentBlue,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = ThemeGold
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = { runSearch() },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text("Find", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(visible = isSearching) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = searchProgress,
                                color = ThemeGold,
                                trackColor = ThemeCardBg,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Scanning page ${searchCurrentPage + 1} of ${viewModel.pageCount}...",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Stop ⏹️",
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        isSearching = false
                                        searchJob?.cancel()
                                    }
                                )
                            }
                        }
                    }

                    if (searchResults.isEmpty() && !isSearching) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isBlank()) "Type a word above to search every page." else "No matches found for \"$searchQuery\".",
                                color = Color.Gray,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
                        ) {
                            items(searchResults) { (resultPage, snippet) ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(ThemeCardBg)
                                        .border(1.dp, ThemeGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                        .clickable {
                                            scope.launch { pagerState.scrollToPage(resultPage) }
                                            isSearching = false
                                            searchJob?.cancel()
                                            showSearchDialog = false
                                        }
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(ThemeGold)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Page ${resultPage + 1}",
                                                color = Color.Black,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text("Teleport ➡️", color = ThemeGoldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = snippet,
                                        color = Color.LightGray,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 8. MODERN SLIDING SETTINGS MODAL DASHBOARD ---
        if (showSettingsDialog) {
            val settingsPagerState = rememberPagerState(pageCount = { 2 })

            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ThemeDarkBg,
                    tonalElevation = 8.dp,
                    modifier = Modifier.widthIn(max = dialogMaxWidth).fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Dialog Header
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("⚙️", fontSize = 22.sp)
                                Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(ThemeCardBg)
                                    .clickable { showSettingsDialog = false },
                                contentAlignment = Alignment.Center
                            ) { Text("✕", fontSize = 14.sp, color = Color.LightGray) }
                        }

                        // Sliding Segmented Tab Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ThemeCardBg)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val tabTitles = listOf("📖 Reader", "ℹ️ About & Social")
                            tabTitles.forEachIndexed { index, title ->
                                val isSelected = settingsPagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) ThemeGold else Color.Transparent)
                                        .clickable { scope.launch { settingsPagerState.animateScrollToPage(index) } }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) Color.Black else Color(0xFFA9B7C6),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sliding Pages Content
                        HorizontalPager(
                            state = settingsPagerState,
                            modifier = Modifier.fillMaxWidth()
                        ) { pageIndex ->
                            if (pageIndex == 0) {
                                // --- PAGE 0: READER SETTINGS ---
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(ThemeCardBg)
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
                                                    Text("Soothing background audio", color = Color(0xFFA9B7C6), fontSize = 12.sp)
                                                }
                                            }
                                            Switch(
                                                checked = isRainPlaying,
                                                onCheckedChange = { isRainPlaying = it },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = ThemeGold,
                                                    checkedTrackColor = ThemeAccentBlue
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
                                                    Text("Volume", color = Color(0xFFA9B7C6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    Text("${(rainVolume * 100).toInt()}%", color = ThemeGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Slider(
                                                    value = rainVolume,
                                                    onValueChange = { rainVolume = it },
                                                    valueRange = 0f..1f,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = ThemeGold,
                                                        activeTrackColor = ThemeGoldLight,
                                                        inactiveTrackColor = ThemeDarkBg
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("PAGE TEXTURE", color = Color(0xFFA9B7C6), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                                        Button(
                                            onClick = { galleryLauncher.launch("image/*") },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if (currentPageStyle == PageStyle.CUSTOM) ThemeGold else ThemeCardBg),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text("🖼️", fontSize = 18.sp)
                                                    Text("Choose from Gallery...", color = if (currentPageStyle == PageStyle.CUSTOM) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                if (currentPageStyle == PageStyle.CUSTOM) {
                                                    Text("✔ Active", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }

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
                                                                .background(if (isSelected) ThemeGold.copy(alpha = 0.2f) else ThemeCardBg)
                                                                .border(
                                                                    width = if (isSelected) 2.dp else 1.dp,
                                                                    color = if (isSelected) ThemeGold else Color.Transparent,
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
                                                                    color = if (isSelected) ThemeGoldLight else Color(0xFFA9B7C6),
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
                            } else {
                                // --- PAGE 1: ABOUT & SOCIAL SETTINGS ---
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // SECTION 1: ABOUT
                                    item {
                                        Text("About", color = Color(0xFFA9B7C6), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(ThemeCardBg)
                                        ) {
                                            // 1. Invite friends
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val sendIntent = Intent().apply {
                                                            action = Intent.ACTION_SEND
                                                            putExtra(Intent.EXTRA_TEXT, "Check out this amazing PDF Book Reader App!" +
                                                                    "https://github.com/SanjeevKumar-24/BOOKL1")
                                                            type = "text/plain"
                                                        }
                                                        context.startActivity(Intent.createChooser(sendIntent, "Invite friends via"))
                                                    }
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Text("🔗", fontSize = 20.sp)
                                                Text("Invite friends to the app", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            }

                                            Divider(color = ThemeDarkBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                            // 2. More Apps
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val url = "https://play.google.com/store"
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                    }
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {

                                                Text("::: ", color = ThemeGoldLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                Text("More Apps", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            }

                                            Divider(color = ThemeDarkBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                            // 3. Send feedback
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                                            data = Uri.parse("mailto:sjv.apps@gmail.com?subject=App%20Feedback")
                                                        }
                                                        try { context.startActivity(emailIntent) } catch (e: Exception) {}
                                                    }
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Text("💬", fontSize = 20.sp)
                                                Text("Send feedback", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            }

                                            Divider(color = ThemeDarkBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))



                                            // 5. Version
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Text("< >", color = ThemeGoldLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                Column {
                                                    Text("Version", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                                    Text("Book Reader Pro 1.0.0", color = Color(0xFFA9B7C6), fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }

                                    // SECTION 2: FOLLOW US
                                    item {
                                        Text("Follow us", color = Color(0xFFA9B7C6), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(ThemeCardBg)
                                        ) {
                                            // 1. LinkedIn
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/sanjeev-kumar-6b7742379?utm_source=share_via&utm_content=profile&utm_medium=member_android")))
                                                    }
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Text("💼", fontSize = 20.sp)
                                                Text("LinkedIn", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            }

                                            Divider(color = ThemeDarkBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                            // 2. GitHub
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SanjeevKumar-24")))
                                                    }
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Text("🐙", fontSize = 20.sp)
                                                Text("GitHub", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            }

                                            Divider(color = ThemeDarkBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                            // 3. Instagram
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/sanjeeveram?igsh=emtuYmJjcGRiM283")))
                                                    }
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Text("📸", fontSize = 20.sp)
                                                Text("Instagram", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }

                                    // SECTION 3: FOOTER BRANDING
                                    item {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1E1E1E))
                                                    .border(2.dp, ThemeGold, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("👑", fontSize = 24.sp)
                                            }
                                            Text("#SJV_AndroidApps", color = Color(0xFFA9B7C6), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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