package com.example.bookl1

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// Helper function to extract the REAL name of the file
fun getRealFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path?.let { File(it).name }
    }
    return result ?: "document_${System.currentTimeMillis()}.pdf"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBookSelected: (File) -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var localFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    // Sorts files by most recent timestamp
    val loadFiles = {
        val cacheFiles = context.cacheDir.listFiles { _, name -> name.endsWith(".pdf") }?.toList() ?: emptyList()
        localFiles = cacheFiles.sortedByDescending { it.lastModified() }
    }

    LaunchedEffect(Unit) { loadFiles() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val realName = getRealFileName(context, selectedUri)
            val tempFile = File(context.cacheDir, realName)

            context.contentResolver.openInputStream(selectedUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Mark as freshly opened
            tempFile.setLastModified(System.currentTimeMillis())
            loadFiles()
            onBookSelected(tempFile)
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A1D), Color(0xFF0B0B0C))
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                containerColor = Color(0xFFFFD700),
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.shadow(12.dp, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Document", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(backgroundBrush)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp).statusBarsPadding())

            // --- SEARCH BAR ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search your library...", color = Color(0xFFAAAAAA), fontSize = 16.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFFFD700)) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedContainerColor = Color(0x1AFFFFFF),
                    focusedContainerColor = Color(0x2AFFFFFF),
                    cursorColor = Color(0xFFFFD700),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(30.dp))
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- RECENTLY OPENED HEADER ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recently Opened",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- GRID OF FILES ---
            val filteredFiles = localFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }

            if (filteredFiles.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0x55FFFFFF), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your library is empty.", color = Color.Gray, fontSize = 16.sp)
                        Text("Tap the gold '+' to import a PDF.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredFiles) { file ->
                        EpicBookGridItem(
                            file = file,
                            onClick = {
                                file.setLastModified(System.currentTimeMillis())
                                onBookSelected(file)
                            },
                            onLongClick = { fileToDelete = file }
                        )
                    }
                }
            }
        }

        // --- DELETION CONFIRMATION DIALOG ---
        if (fileToDelete != null) {
            AlertDialog(
                onDismissRequest = { fileToDelete = null },
                title = { Text("Remove Document") },
                text = { Text("Are you sure you want to remove '${fileToDelete?.name}' from your library? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            fileToDelete?.delete()
                            loadFiles()
                            fileToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) { Text("Delete", color = Color.White) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { fileToDelete = null }) { Text("Cancel") }
                },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpicBookGridItem(file: File, onClick: () -> Unit, onLongClick: () -> Unit) {
    val df = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = df.format(Date(file.lastModified()))

    val sizeKb = file.length() / 1024
    val sizeString = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"

    // Background thumbnail state
    var pdfThumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Generates a thumbnail image from the PDF on a background thread
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val width = 300
                    val height = (width.toFloat() / page.width * page.height).toInt()
                    val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)

                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)

                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    descriptor.close()
                    pdfThumbnail = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Fallback colored gradient logic
    val colorPalette = listOf(Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF3949AB), Color(0xFF039BE5), Color(0xFF00897B), Color(0xFFF4511E))
    val primaryColor = colorPalette[abs(file.name.hashCode()) % colorPalette.size]
    val cardGradient = Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.8f), primaryColor.copy(alpha = 0.3f)))

    Column(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .width(100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- BOOK COVER ---
        Box(
            modifier = Modifier
                .aspectRatio(0.70f)
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (pdfThumbnail != null) Modifier.background(Color.White)
                    else Modifier.background(cardGradient)
                )
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (pdfThumbnail != null) {
                // Show the real PDF cover
                Image(
                    bitmap = pdfThumbnail!!.asImageBitmap(),
                    contentDescription = "Cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Show the fallback gradient if loading fails
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color(0x44000000), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = file.nameWithoutExtension,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, offset = Offset(2f, 2f), blurRadius = 4f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- METADATA ---
        Text(
            text = file.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(sizeString, color = Color(0xFFAAAAAA), fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text("•", color = Color(0xFFAAAAAA), fontSize = 10.sp)
            Text(dateString, color = Color(0xFFAAAAAA), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}