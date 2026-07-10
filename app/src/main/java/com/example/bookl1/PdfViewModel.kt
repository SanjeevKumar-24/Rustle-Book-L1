package com.example.bookl1

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    override fun onCleared() {
        super.onCleared()
        pdfEngine?.close() // Prevent memory leaks!
    }
}