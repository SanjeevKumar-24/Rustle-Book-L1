package com.example.bookl1

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class PdfEngine(pdfFile: File) {
    private val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
    private val pdfRenderer = PdfRenderer(fileDescriptor)

    val pageCount: Int get() = pdfRenderer.pageCount

    fun getPageBitmap(pageIndex: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // ADD THIS LINE: Paint the blank bitmap white before drawing the PDF!
        bitmap.eraseColor(android.graphics.Color.WHITE)

        val page = pdfRenderer.openPage(pageIndex)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        return bitmap
    }

    fun close() {
        pdfRenderer.close()
        fileDescriptor.close()
    }
}
