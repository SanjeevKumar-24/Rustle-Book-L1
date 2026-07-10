package com.example.bookl1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import java.io.File
import java.io.FileOutputStream


class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // CRITICAL: This tells Android to actually build the Activity
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Copy a sample PDF from your assets folder
        val samplePdf = copyAssetToCache("sample.pdf")

        // 2. Tell the ViewModel to open that file
        if (samplePdf != null) {
            viewModel.openBook(samplePdf)
        }

        // 3. Launch the screen
        setContent {
            BookScreen(viewModel = viewModel)
        }
    }

    // Helper function to extract a file from the 'assets' folder for testing
    private fun copyAssetToCache(fileName: String): File? {
        return try {
            val cacheFile = File(cacheDir, fileName)
            // Only copy if we haven't done it already
            if (!cacheFile.exists()) {
                assets.open(fileName).use { inputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}