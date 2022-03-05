package sushi.hardcore.droidfs.file_viewers

import android.view.Menu
import android.view.MenuItem
import org.grapheneos.pdfviewer.PdfViewer
import sushi.hardcore.droidfs.databinding.ActivityPdfViewerBinding
import java.io.ByteArrayInputStream
import java.io.File

class PdfViewer: FileViewerActivity() {
    private lateinit var pdfViewer: PdfViewer

    override fun hideSystemUi() {
        //don't hide system ui
    }

    override fun getFileType(): String {
        return "pdf"
    }

    override fun viewFile() {
        pdfViewer = ActivityPdfViewerBinding.inflate(layoutInflater).root
        val fileName = File(filePath).name
        title = fileName
        pdfViewer.activity = this
        setContentView(pdfViewer)
        val fileSize = gocryptfsVolume.getSize(filePath)
        loadWholeFile(filePath, fileSize)?.let {
            pdfViewer.loadPdf(ByteArrayInputStream(it), fileName, fileSize)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        pdfViewer.onCreateOptionMenu(menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        return pdfViewer.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return pdfViewer.onOptionsItemSelected(item) || super.onOptionsItemSelected(item)
    }
}
