package com.example.bookl1

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfViewModel : ViewModel() {
    private var pdfEngine: PdfEngine? = null
    var pageCount by mutableIntStateOf(0)
        private set

    // Initialize the engine with your file
    fun openBook(file: File) {
        pdfEngine = PdfEngine(file)
        pageCount = pdfEngine?.pageCount ?: 0
    }

    // Suspend function to load the image off the main UI thread
    suspend fun getPageImage(pageIndex: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            // Hardcoding 1080x1920 for simplicity, but you can pass actual screen dimensions here
            pdfEngine?.getPageBitmap(pageIndex, 1080, 1920)
        }
    }

    /**
     * Imports a PDF from an external URI on a background thread (Dispatchers.IO)
     * to prevent Main Thread ANR freezes when loading massive files (100MB+).
     */
    fun importAndOpenUri(context: Context, uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                copyUriToCache(context, uri)
            }
            if (file != null) {
                ActiveBook.fileName = file.name
                openBook(file)
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            var fileName = "shared_${System.currentTimeMillis()}.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            val cacheFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfEngine?.close() // Prevent memory leaks!
    }
}