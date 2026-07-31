package com.example.bookl1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
val ThemeDarkBg = Color(0xFF121212)
val ThemeCardBg = Color(0xFF1E1E1E)
val ThemeAccentBlue = Color(0xFF2C2C2C)
val ThemeGold = Color(0xFFD4AF37)
val ThemeGoldLight = Color(0xFFF3E5AB)

val PenColors = listOf(Color.Black, Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047))
val HighColors = listOf(Color.Yellow.copy(alpha = 0.4f), Color(0xFFFF4081).copy(alpha = 0.4f), Color(0xFF00E676).copy(alpha = 0.4f))

// --- 2. DATA MODELS & PERSISTENCE ---
enum class Tool { NONE, PEN, HIGHLIGHT, ERASER, NOTE, SCANNER }
data class DrawStroke(val points: List<Offset>, val tool: Tool, val color: Color = Color.Black)
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
    NIGHT("Night Mode", Color(0xFF121212), null),
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

// Safely extracts Activity to prevent Context crashes
fun Context.getActivity(): Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

private fun isDefaultOrSampleFile(fileName: String): Boolean {
    val cleanName = fileName.trim().lowercase()
    return cleanName.isEmpty() || cleanName == "default.pdf" || cleanName == "sample.pdf"
}

fun saveAnnotations(context: Context, strokes: Map<Int, List<DrawStroke>>, notes: Map<Int, List<StickyNoteData>>, bookmarks: List<Int>) {
    if (isDefaultOrSampleFile(ActiveBook.fileName)) return
    val sStrokes = HashMap<Int, List<SavedStroke>>()
    strokes.forEach { (page, list) ->
        sStrokes[page] = list.map {
            val toolStr = "${it.tool.name}_${it.color.value.toLong()}"
            SavedStroke(it.points.map { p -> p.x }, it.points.map { p -> p.y }, toolStr)
        }
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
    if (buggySharedFile.exists()) buggySharedFile.delete()
    val buggySampleFile = File(context.filesDir, "sample.pdf.dat")
    if (buggySampleFile.exists()) buggySampleFile.delete()
    if (isDefaultOrSampleFile(ActiveBook.fileName)) return null
    val memoryFile = File(context.filesDir, "${ActiveBook.fileName}.dat")
    if (!memoryFile.exists()) return null
    return try {
        ObjectInputStream(FileInputStream(memoryFile)).use { it.readObject() as SavedData }
    } catch (e: Exception) { null }
}

suspend fun scanBitmapForText(bitmap: Bitmap): String = suspendCoroutine { cont ->
    try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resume("") }
    } catch (e: Exception) { cont.resume("") }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookScreen(viewModel: PdfViewModel, onBackClicked: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { viewModel.pageCount })
    val context = LocalContext.current
    val activity = context.getActivity()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val density = LocalDensity.current

    // --- TABLET, SCREEN SIZE, & ORIENTATION DETECTION ---
    val isTablet = remember(configuration) {
        configuration.smallestScreenWidthDp >= 600 ||
                (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridColumns = if (isTablet) 4 else 2
    val dialogMaxWidth = if (isTablet) 600.dp else 400.dp
    val toolbarIconSize = if (isTablet) 44.dp else 36.dp
    val toolbarIconFontSize = if (isTablet) 24.sp else 20.sp

    var areUiBarsVisible by remember { mutableStateOf(true) }
    val showMainUi = areUiBarsVisible || !isLandscape

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isFirstLoad by remember { mutableStateOf(true) }

    // Features State
    var showAdvancedMenu by remember { mutableStateOf(false) }
    var isTiltToTurnEnabled by remember { mutableStateOf(false) }

    // --- 1. FULLSCREEN IMMERSIVE MODE ---
    LaunchedEffect(isLandscape) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowInsetsControllerCompat(window, view)
            if (isLandscape) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // --- 2. ACCELEROMETER SENSOR: 1 PAGE PER FLIP & HOLD TO ROTATE ---
    DisposableEffect(context, isLandscape, isTiltToTurnEnabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var lastTiltState = 0
        var tiltStartTime = 0L
        var hasFlipped = false
        var hasRotated = false

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || viewModel.pageCount == 0) return
                val xAxis = event.values[0]
                val currentTime = System.currentTimeMillis()

                // Neutral Zone (Phone held flat/normal) -> Resets the flip switch
                if (xAxis in -3f..3f) {
                    lastTiltState = 0
                    tiltStartTime = 0L
                    hasFlipped = false
                    hasRotated = false
                }
                // Tilted Left (Next Page)
                else if (xAxis > 6.0f) {
                    if (lastTiltState != 1) {
                        lastTiltState = 1
                        tiltStartTime = currentTime
                        hasFlipped = false
                        hasRotated = false
                    }

                    // FLIP ACTION: Fire exactly once per tilt (if feature is enabled)
                    if (isTiltToTurnEnabled && !isLandscape && !hasFlipped && !pagerState.isScrollInProgress) {
                        hasFlipped = true
                        if (pagerState.currentPage < viewModel.pageCount - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    }

                    // ROTATE ACTION: Always active. If held for > 1.5 seconds
                    if (!hasRotated && tiltStartTime > 0 && (currentTime - tiltStartTime > 1500L)) {
                        hasRotated = true
                        activity?.requestedOrientation = if (isLandscape)
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
                // Tilted Right (Previous Page)
                else if (xAxis < -6.0f) {
                    if (lastTiltState != -1) {
                        lastTiltState = -1
                        tiltStartTime = currentTime
                        hasFlipped = false
                        hasRotated = false
                    }

                    // FLIP ACTION: Fire exactly once per tilt (if feature is enabled)
                    if (isTiltToTurnEnabled && !isLandscape && !hasFlipped && !pagerState.isScrollInProgress) {
                        hasFlipped = true
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    }

                    // ROTATE ACTION: Always active. If held for > 1.5 seconds
                    if (!hasRotated && tiltStartTime > 0 && (currentTime - tiltStartTime > 1500L)) {
                        hasRotated = true
                        activity?.requestedOrientation = if (isLandscape)
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    var bookmarkToastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bookmarkToastMessage) {
        if (bookmarkToastMessage != null) {
            delay(2000L)
            bookmarkToastMessage = null
        }
    }

    var currentTool by remember { mutableStateOf(Tool.NONE) }
    var currentPenColor by remember { mutableStateOf(PenColors[0]) }
    var currentHighlightColor by remember { mutableStateOf(HighColors[0]) }
    var showColorPalette by remember { mutableStateOf(false) }
    var isToolbarVisible by remember { mutableStateOf(true) }

    var currentPageStyle by remember { mutableStateOf(PageStyle.TEXTURE_2) }
    var isRainPlaying by remember { mutableStateOf(false) }

    // Dialog States
    var showTextureDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
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
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    var toolboxOffsetX by remember { mutableFloatStateOf(0f) }
    var toolboxOffsetY by remember { mutableFloatStateOf(0f) }

    var showPageOverview by remember { mutableStateOf(false) }
    var showBookmarksList by remember { mutableStateOf(false) }

    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchProgress by remember { mutableFloatStateOf(0f) }
    var searchCurrentPage by remember { mutableIntStateOf(0) }
    val searchResults = remember { mutableStateListOf<Pair<Int, String>>() }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isReadingLoading by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { isSpeaking = true }
            override fun onDone(utteranceId: String?) { isSpeaking = false }
            override fun onError(utteranceId: String?) { isSpeaking = false }
        })
        tts = engine
        onDispose {
            try { engine.stop(); engine.shutdown() } catch (e: Exception) { e.printStackTrace() }
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
    val redoStrokesMap = remember(ActiveBook.fileName) { mutableStateMapOf<Int, MutableList<DrawStroke>>() }
    val notesMap = remember(ActiveBook.fileName) { mutableStateMapOf<Int, MutableList<StickyNoteData>>() }
    val bookmarks = remember(ActiveBook.fileName) { mutableStateListOf<Int>() }

    var scannerPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var scannedTextResult by remember { mutableStateOf("Scanning...") }

    val pageFlipPlayer = remember { try { MediaPlayer.create(context, R.raw.page_flip) } catch (e: Exception) { null } }
    val rainPlayer = remember { try { MediaPlayer.create(context, R.raw.rain)?.apply { isLooping = true } } catch (e: Exception) { null } }

    LaunchedEffect(isRainPlaying) {
        if (rainPlayer == null && isRainPlaying) {
            Toast.makeText(context, "Cannot play: res/raw/rain.mp3 is corrupt", Toast.LENGTH_LONG).show()
        } else {
            try {
                rainPlayer?.setVolume(0.5f, 0.5f)
                if (isRainPlaying) {
                    if (rainPlayer?.isPlaying == false) rainPlayer.start()
                } else {
                    if (rainPlayer?.isPlaying == true) rainPlayer.pause()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
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
        redoStrokesMap.clear()
        notesMap.clear()
        bookmarks.clear()
        loadAnnotations(context)?.let { saved ->
            saved.strokes.forEach { (page, sList) ->
                strokesMap[page] = sList.map {
                    val parts = it.tool.split("_")
                    val tool = Tool.valueOf(parts[0])
                    val color = if (parts.size > 1) Color(parts[1].toULong()) else (if (tool == Tool.HIGHLIGHT) HighColors[0] else PenColors[0])
                    DrawStroke(it.xs.zip(it.ys) { x, y -> Offset(x, y) }, tool, color)
                }.toMutableList()
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
                if (pageFlipPlayer?.isPlaying == true) pageFlipPlayer.pause()
                pageFlipPlayer?.seekTo(0)
                pageFlipPlayer?.start()
            } catch (e: Exception) { e.printStackTrace() }
        } else isFirstLoad = false
    }

    Box(modifier = Modifier.fillMaxSize().background(ThemeDarkBg)) {

        // --- THE PDF VIEWER ---
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

                        // --- SMART A4 CALCULATION ---
                        val imgRatio = if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else 1f
                        val screenRatio = if (screenHeight > 0) screenWidth / screenHeight else 1f

                        val baseWidth: Float
                        val baseHeight: Float

                        if (isLandscape) {
                            // Fit-to-Width for Landscape: PDF spans entire screen width
                            baseWidth = screenWidth
                            baseHeight = screenWidth / imgRatio
                        } else {
                            // Fit-Inside for Portrait
                            if (screenRatio > imgRatio) {
                                baseHeight = screenHeight
                                baseWidth = screenHeight * imgRatio
                            } else {
                                baseWidth = screenWidth
                                baseHeight = screenWidth / imgRatio
                            }
                        }

                        val unscaleOffset = { screenOffset: Offset ->
                            val pivotX = screenWidth / 2f
                            val pivotY = screenHeight / 2f
                            val centeredX = (screenOffset.x - offsetX - pivotX) / scale
                            val centeredY = (screenOffset.y - offsetY - pivotY) / scale
                            val boxX = centeredX + (baseWidth / 2f)
                            val boxY = centeredY + (baseHeight / 2f)

                            Offset(boxX / baseWidth, boxY / baseHeight)
                        }

                        val eraseAt: (Offset) -> Unit = { normPoint ->
                            val currentStrokes = strokesMap[page] ?: mutableListOf()
                            val newStrokes = currentStrokes.filter { stroke ->
                                stroke.points.none { p ->
                                    val px = if (p.x > 2f) p.x else p.x * baseWidth
                                    val py = if (p.y > 2f) p.y else p.y * baseHeight
                                    val nx = normPoint.x * baseWidth
                                    val ny = normPoint.y * baseHeight
                                    val dx = px - nx
                                    val dy = py - ny
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

                                                    val minX = scannerPoints.minOf { it.x * baseWidth }
                                                    val maxX = scannerPoints.maxOf { it.x * baseWidth }
                                                    val minY = scannerPoints.minOf { it.y * baseHeight }
                                                    val maxY = scannerPoints.maxOf { it.y * baseHeight }

                                                    val imgScaleX = if (baseWidth > 0) bitmap.width.toFloat() / baseWidth else 1f
                                                    val imgScaleY = if (baseHeight > 0) bitmap.height.toFloat() / baseHeight else 1f

                                                    val bMinX = (minX * imgScaleX).toInt()
                                                    val bMaxX = (maxX * imgScaleX).toInt()
                                                    val bMinY = (minY * imgScaleY).toInt()
                                                    val bMaxY = (maxY * imgScaleY).toInt()

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
                                                        .addOnFailureListener { _ -> scannedTextResult = "Error scanning text." }
                                                } else {
                                                    scannerPoints = emptyList()
                                                }
                                            }
                                        )
                                    } else if (currentTool == Tool.PEN || currentTool == Tool.HIGHLIGHT || currentTool == Tool.ERASER) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val normOffset = unscaleOffset(offset)
                                                if (currentTool == Tool.ERASER) eraseAt(normOffset)
                                                else currentLiveStroke = listOf(normOffset)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val normOffset = unscaleOffset(change.position)
                                                if (currentTool == Tool.ERASER) eraseAt(normOffset)
                                                else currentLiveStroke = currentLiveStroke + normOffset
                                            },
                                            onDragEnd = {
                                                if (currentLiveStroke.isNotEmpty() && currentTool != Tool.ERASER) {
                                                    val activeColor = if (currentTool == Tool.PEN) currentPenColor else currentHighlightColor
                                                    val list = strokesMap[page]?.toMutableList() ?: mutableListOf()
                                                    list.add(DrawStroke(currentLiveStroke, currentTool, activeColor))
                                                    strokesMap[page] = list
                                                    redoStrokesMap[page]?.clear()
                                                    currentLiveStroke = emptyList()
                                                    saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                                }
                                            }
                                        )
                                    } else if (currentTool == Tool.NOTE) {
                                        detectTapGestures(onTap = { offset ->
                                            val normOffset = unscaleOffset(offset)
                                            val list = notesMap[page] ?: mutableListOf()
                                            list.add(StickyNoteData(java.util.UUID.randomUUID().toString(), normOffset, ""))
                                            notesMap[page] = list
                                            currentTool = Tool.NONE
                                            saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                        })
                                    }
                                }
                                .pointerInput(currentTool) {
                                    if (currentTool == Tool.NONE) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown()
                                            var isTap = true
                                            do {
                                                val event = awaitPointerEvent()
                                                val zoom = event.calculateZoom()
                                                val pan = event.calculatePan()

                                                if (event.changes.size > 1 || kotlin.math.abs(zoom - 1f) > 0.01f || pan.getDistance() > 5f) {
                                                    isTap = false
                                                }

                                                if (event.changes.size >= 2) {
                                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                                    val cW = baseWidth * scale
                                                    val cH = baseHeight * scale
                                                    val mX = maxOf(0f, (cW - screenWidth) / 2f)
                                                    val mY = maxOf(0f, (cH - screenHeight) / 2f)
                                                    offsetX = (offsetX + pan.x).coerceIn(-mX, mX)
                                                    offsetY = (offsetY + pan.y).coerceIn(-mY, mY)
                                                } else if (scale > 1.01f || (isLandscape && baseHeight > screenHeight)) {
                                                    val cW = baseWidth * scale
                                                    val cH = baseHeight * scale
                                                    val mX = maxOf(0f, (cW - screenWidth) / 2f)
                                                    val mY = maxOf(0f, (cH - screenHeight) / 2f)
                                                    offsetX = (offsetX + pan.x).coerceIn(-mX, mX)
                                                    offsetY = (offsetY + pan.y).coerceIn(-mY, mY)
                                                }
                                            } while (event.changes.any { it.pressed })

                                            // Toggle UI bars on quick single tap
                                            if (isTap && scale <= 1.05f) {
                                                areUiBarsVisible = !areUiBarsVisible
                                                showColorPalette = false
                                            }

                                            if (scale <= 1.05f) {
                                                scale = 1f
                                                if (baseWidth <= screenWidth) offsetX = 0f
                                                if (baseHeight <= screenHeight) offsetY = 0f
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {

                            // PERFECTLY SIZED INNER BOX (PREVENTS SQUISHING)
                            Box(
                                modifier = Modifier
                                    .requiredWidth(with(density) { baseWidth.toDp() })
                                    .requiredHeight(with(density) { baseHeight.toDp() })
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offsetX
                                        translationY = offsetY
                                    }
                            ) {
                                val bgBitmap = customBgBitmap
                                val resId = currentPageStyle.drawableRes
                                val isNightMode = currentPageStyle == PageStyle.NIGHT

                                val invertMatrix = remember {
                                    ColorMatrix(
                                        floatArrayOf(
                                            -1f, 0f, 0f, 0f, 255f,
                                            0f, -1f, 0f, 0f, 255f,
                                            0f, 0f, -1f, 0f, 255f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                }

                                if (currentPageStyle == PageStyle.CUSTOM && bgBitmap != null) {
                                    Image(bitmap = bgBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else if (resId != null) {
                                    Image(painter = painterResource(id = resId), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Box(modifier = Modifier.fillMaxSize().background(currentPageStyle.color))
                                }

                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    colorFilter = if (isNightMode) ColorFilter.colorMatrix(invertMatrix) else null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            blendMode = if (isNightMode) BlendMode.Screen else BlendMode.Multiply
                                        }
                                )

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val allPaths = (strokesMap[page] ?: emptyList()) + if (currentLiveStroke.isNotEmpty()) listOf(DrawStroke(currentLiveStroke, currentTool, if (currentTool == Tool.PEN) currentPenColor else currentHighlightColor)) else emptyList()
                                    for (stroke in allPaths) {
                                        if (stroke.points.size < 2) continue
                                        val path = Path().apply {
                                            val first = stroke.points.first()
                                            moveTo(if (first.x > 2f) first.x else first.x * baseWidth,
                                                if (first.y > 2f) first.y else first.y * baseHeight)
                                            for (i in 1 until stroke.points.size) {
                                                val p = stroke.points[i]
                                                lineTo(if (p.x > 2f) p.x else p.x * baseWidth,
                                                    if (p.y > 2f) p.y else p.y * baseHeight)
                                            }
                                        }

                                        val colorToDraw = if (stroke.color == Color.Black && isNightMode) Color.White else stroke.color
                                        val width = if (stroke.tool == Tool.HIGHLIGHT) 45f else 8f
                                        drawPath(path, colorToDraw, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }

                                    if (currentTool == Tool.SCANNER && scannerPoints.isNotEmpty()) {
                                        val scannerPath = Path().apply {
                                            val first = scannerPoints.first()
                                            moveTo(first.x * baseWidth, first.y * baseHeight)
                                            for (i in 1 until scannerPoints.size) {
                                                val p = scannerPoints[i]
                                                lineTo(p.x * baseWidth, p.y * baseHeight)
                                            }
                                            close()
                                        }
                                        drawPath(path = scannerPath, color = ThemeGold, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f))))
                                        drawPath(path = scannerPath, color = ThemeGold.copy(alpha = 0.2f))
                                    }
                                }

                                notesMap[page]?.forEach { note ->
                                    var showDialog by remember { mutableStateOf(false) }
                                    var noteText by remember(note.id, note.text) { mutableStateOf(note.text) }
                                    var notePosition by remember(note.id) { mutableStateOf(note.position) }

                                    val drawX = if (notePosition.x > 2f) notePosition.x else notePosition.x * baseWidth
                                    val drawY = if (notePosition.y > 2f) notePosition.y else notePosition.y * baseHeight

                                    Box(
                                        modifier = Modifier
                                            .offset { IntOffset(drawX.toInt() - 30, drawY.toInt() - 30) }
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
                                                        val normDx = (dragAmount.x / scale) / baseWidth
                                                        val normDy = (dragAmount.y / scale) / baseHeight

                                                        val currX = if (notePosition.x > 2f) notePosition.x / baseWidth else notePosition.x
                                                        val currY = if (notePosition.y > 2f) notePosition.y / baseHeight else notePosition.y

                                                        notePosition = Offset(currX + normDx, currY + normDy)
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
                            }

                            // --- SCANNED TEXT MODAL ---
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
                        }

                        if (bookmarks.contains(page)) {
                            Text("🔖", fontSize = 45.sp, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).zIndex(2f))
                        }
                    }
                }
            }
        }

        // --- 2. TOP NAVIGATION BAR WITH AUDIO BANNER ---
        AnimatedVisibility(
            visible = showMainUi,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            Column {
                Column(modifier = Modifier.background(ThemeCardBg)) {
                    Spacer(modifier = Modifier.statusBarsPadding())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isTablet) 32.dp else 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 16.dp)) {
                            Text(text = "⬅️", fontSize = toolbarIconFontSize, color = Color.White, modifier = Modifier.clickable { onBackClicked() })
                            Text(text = "🔲", fontSize = toolbarIconFontSize, color = Color.White, modifier = Modifier.clickable { showPageOverview = true })
                            Text(text = "📑", fontSize = toolbarIconFontSize, color = Color.White, modifier = Modifier.clickable { showBookmarksList = true })
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val pg = pagerState.currentPage
                            val canUndo = strokesMap[pg]?.isNotEmpty() == true
                            val canRedo = redoStrokesMap[pg]?.isNotEmpty() == true

                            Text(
                                text = "↶",
                                fontSize = toolbarIconFontSize,
                                color = if (canUndo) Color.White else Color.DarkGray,
                                modifier = Modifier.clickable(enabled = canUndo) {
                                    val list = strokesMap[pg]?.toMutableList() ?: mutableListOf()
                                    if (list.isNotEmpty()) {
                                        val last = list.removeAt(list.lastIndex)
                                        strokesMap[pg] = list
                                        val rList = redoStrokesMap[pg]?.toMutableList() ?: mutableListOf()
                                        rList.add(last)
                                        redoStrokesMap[pg] = rList
                                        saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                    }
                                }
                            )

                            Text(
                                text = "↷",
                                fontSize = toolbarIconFontSize,
                                color = if (canRedo) Color.White else Color.DarkGray,
                                modifier = Modifier.clickable(enabled = canRedo) {
                                    val rList = redoStrokesMap[pg]?.toMutableList() ?: mutableListOf()
                                    if (rList.isNotEmpty()) {
                                        val last = rList.removeAt(rList.lastIndex)
                                        redoStrokesMap[pg] = rList
                                        val list = strokesMap[pg]?.toMutableList() ?: mutableListOf()
                                        list.add(last)
                                        strokesMap[pg] = list
                                        saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                    }
                                }
                            )

                            Box {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp).clickable { showAdvancedMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showAdvancedMenu,
                                    onDismissRequest = { showAdvancedMenu = false },
                                    modifier = Modifier.background(ThemeCardBg).border(1.dp, ThemeGold, RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Read Aloud", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isReadingLoading) Icons.Default.HourglassEmpty else if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                                contentDescription = "Read Aloud",
                                                tint = ThemeGold
                                            )
                                        },
                                        onClick = { toggleReadAloud(); showAdvancedMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Search Book", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = ThemeGold) },
                                        onClick = { showSearchDialog = true; showAdvancedMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tilt to Turn Page", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Icon(Icons.Default.ScreenRotation, contentDescription = "Tilt", tint = ThemeGold) },
                                        trailingIcon = {
                                            Switch(
                                                checked = isTiltToTurnEnabled,
                                                onCheckedChange = { isTiltToTurnEnabled = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = ThemeGold, checkedTrackColor = ThemeAccentBlue)
                                            )
                                        },
                                        onClick = { isTiltToTurnEnabled = !isTiltToTurnEnabled }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ambient Rain", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Text("🌧️", fontSize = 18.sp) },
                                        trailingIcon = {
                                            Switch(
                                                checked = isRainPlaying,
                                                onCheckedChange = { isRainPlaying = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = ThemeGold, checkedTrackColor = ThemeAccentBlue)
                                            )
                                        },
                                        onClick = { isRainPlaying = !isRainPlaying }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Dark Mode", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Text("🌙", fontSize = 18.sp) },
                                        trailingIcon = {
                                            Switch(
                                                checked = currentPageStyle == PageStyle.NIGHT,
                                                onCheckedChange = { currentPageStyle = if (it) PageStyle.NIGHT else PageStyle.TEXTURE_2 },
                                                colors = SwitchDefaults.colors(checkedThumbColor = ThemeGold, checkedTrackColor = ThemeAccentBlue)
                                            )
                                        },
                                        onClick = { currentPageStyle = if (currentPageStyle == PageStyle.NIGHT) PageStyle.TEXTURE_2 else PageStyle.NIGHT }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Page Texture", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Text("🎨", fontSize = 18.sp) },
                                        onClick = { showTextureDialog = true; showAdvancedMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("About & Social", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Text("ℹ️", fontSize = 18.sp) },
                                        onClick = { showAboutDialog = true; showAdvancedMenu = false }
                                    )
                                }
                            }
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
        }

        // --- 3. THE HORIZONTAL DRAGGABLE TOOLBOX (WITH COLOR PICKER) ---
        AnimatedVisibility(
            visible = isToolbarVisible && showMainUi,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isTablet) 90.dp else 72.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier.offset { IntOffset(toolboxOffsetX.roundToInt(), toolboxOffsetY.roundToInt()) }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // Floating Color Palette Row
                    AnimatedVisibility(visible = showColorPalette && (currentTool == Tool.PEN || currentTool == Tool.HIGHLIGHT)) {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ThemeCardBg)
                                .border(1.dp, ThemeGold.copy(alpha=0.5f), RoundedCornerShape(50))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val colors = if (currentTool == Tool.PEN) PenColors else HighColors
                            val selectedColor = if (currentTool == Tool.PEN) currentPenColor else currentHighlightColor

                            colors.forEach { c ->
                                val isNight = currentPageStyle == PageStyle.NIGHT
                                val displayColor = if (c == Color.Black && isNight) Color.White else c

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(displayColor)
                                        .border(2.dp, if (c == selectedColor) ThemeGold else Color.Transparent, CircleShape)
                                        .clickable {
                                            if (currentTool == Tool.PEN) currentPenColor = c else currentHighlightColor = c
                                            showColorPalette = false // Auto-hide palette after selection
                                        }
                                )
                            }
                        }
                    }

                    // Main Toolbox
                    Row(
                        modifier = Modifier
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
                            .padding(horizontal = if (isTablet) 20.dp else 12.dp, vertical = if (isTablet) 10.dp else 6.dp)
                            .horizontalScroll(rememberScrollState()),
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
                                        if (currentTool == t && (t == Tool.PEN || t == Tool.HIGHLIGHT)) {
                                            showColorPalette = !showColorPalette
                                        } else {
                                            currentTool = t
                                            showColorPalette = (t == Tool.PEN || t == Tool.HIGHLIGHT)
                                            scannerPoints = emptyList()

                                            // Hides the toolbox when Hand/Palm tool is tapped
                                            if (t == Tool.NONE) {
                                                isToolbarVisible = false
                                            }
                                        }
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
                                    val pageNum = pagerState.currentPage + 1
                                    if (isBookmarked) {
                                        bookmarks.remove(pagerState.currentPage)
                                        bookmarkToastMessage = "Removed bookmark from Page $pageNum"
                                    } else {
                                        bookmarks.add(pagerState.currentPage)
                                        bookmarkToastMessage = "Page $pageNum Bookmarked 🔖"
                                    }
                                    saveAnnotations(context, strokesMap, notesMap, bookmarks)
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(text = if (isBookmarked) "🔖" else "➕", fontSize = toolbarIconFontSize) }
                    }
                }
            }
        }

        // --- FLOATING EDIT RE-OPEN BUTTON ---
        AnimatedVisibility(
            visible = !isToolbarVisible && showMainUi,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = if (isTablet) 90.dp else 72.dp),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            FloatingActionButton(
                onClick = {
                    isToolbarVisible = true
                    currentTool = Tool.PEN // Re-open with pen selected
                },
                containerColor = ThemeCardBg,
                contentColor = ThemeGold,
                shape = CircleShape,
                modifier = Modifier.border(1.dp, ThemeGold, CircleShape)
            ) {
                Text("🖊️", fontSize = 24.sp)
            }
        }

        // --- FLOATING BOOKMARK TOAST NOTIFICATION ---
        AnimatedVisibility(
            visible = bookmarkToastMessage != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isTablet) 64.dp else 52.dp)
                .zIndex(10f),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Surface(
                color = ThemeCardBg,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ThemeGold),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = bookmarkToastMessage ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- 4. BOTTOM SLIDER OVERLAY ---
        AnimatedVisibility(
            visible = showMainUi,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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

                    val maxPage = maxOf(0.001f, (viewModel.pageCount - 1).toFloat())
                    Slider(
                        value = pagerState.currentPage.toFloat(),
                        onValueChange = { scope.launch { pagerState.scrollToPage(it.toInt()) } },
                        valueRange = 0f..maxPage,
                        colors = SliderDefaults.colors(
                            thumbColor = ThemeGold,
                            activeTrackColor = ThemeGoldLight,
                            inactiveTrackColor = ThemeDarkBg
                        ),
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                }
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

        // --- 8. DIALOGS (TEXTURE & ABOUT) ---
        if (showTextureDialog) {
            AlertDialog(
                onDismissRequest = { showTextureDialog = false },
                containerColor = ThemeCardBg,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Page Texture", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { galleryLauncher.launch("image/*"); showTextureDialog = false },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (currentPageStyle == PageStyle.CUSTOM) ThemeGold else ThemeDarkBg)
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

                        val presets = PageStyle.values().filter { it != PageStyle.CUSTOM && it != PageStyle.NIGHT }
                        presets.chunked(2).forEach { rowStyles ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowStyles.forEach { style ->
                                    val isSelected = currentPageStyle == style
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) ThemeGold.copy(alpha = 0.2f) else ThemeDarkBg)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) ThemeGold else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { currentPageStyle = style; showTextureDialog = false }
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
                                                color = if (isSelected) ThemeGoldLight else Color.LightGray,
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
                },
                confirmButton = {
                    TextButton(onClick = { showTextureDialog = false }) { Text("Close", color = ThemeGold) }
                }
            )
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                containerColor = ThemeCardBg,
                shape = RoundedCornerShape(24.dp),
                title = { Text("About & Social", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ThemeDarkBg)
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

                                Divider(color = ThemeCardBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

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

                                Divider(color = ThemeCardBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

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

                                Divider(color = ThemeCardBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                // 4. Version
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
                                        Text("Book Reader Pro 1.0.0", color = Color.LightGray, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ThemeDarkBg)
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

                                Divider(color = ThemeCardBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

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

                                Divider(color = ThemeCardBg, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

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
                                Text("#SJV_AndroidApps", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) { Text("Close", color = ThemeGold) }
                }
            )
        }
    }
}