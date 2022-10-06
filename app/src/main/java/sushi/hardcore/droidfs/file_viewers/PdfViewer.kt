package sushi.hardcore.droidfs.file_viewers

import android.view.Menu
import android.view.MenuItem
import app.grapheneos.pdfviewer.PdfViewer
import java.io.ByteArrayInputStream
import java.io.File

class PdfViewer: FileViewerActivity() {
    init {
        applyCustomTheme = false
    }
    override var fullscreenMode = false
    private lateinit var pdfViewer: PdfViewer

    override fun getFileType(): String {
        return "pdf"
    }

    override fun viewFile() {
        pdfViewer = PdfViewer(this)
        val fileName = File(filePath).name
        title = fileName
        val fileSize = encryptedVolume.getAttr(filePath)?.size
        loadWholeFile(filePath, fileSize) {
            pdfViewer.loadPdf(ByteArrayInputStream(it), fileName, fileSize)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        pdfViewer.onCreateOptionMenu(menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onResume() {
        super.onResume()
        pdfViewer.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfViewer.onDestroy()
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        return pdfViewer.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return pdfViewer.onOptionsItemSelected(item) || super.onOptionsItemSelected(item)
    }
}
