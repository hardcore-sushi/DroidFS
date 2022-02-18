package sushi.hardcore.droidfs.file_viewers

import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityPdfViewerBinding
import java.io.ByteArrayInputStream
import java.io.File

class PdfViewer: FileViewerActivity() {
    private lateinit var binding: ActivityPdfViewerBinding

    override fun hideSystemUi() {
        //don't hide system ui
    }

    override fun getFileType(): String {
        return "pdf"
    }

    override fun viewFile() {
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        val toolbar = binding.root.findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        title = ""
        val titleText = toolbar.findViewById<TextView>(R.id.title_text)
        val fileName = File(filePath).name
        titleText.text = fileName
        binding.pdfViewer.activity = this
        setContentView(binding.root)
        val fileSize = gocryptfsVolume.getSize(filePath)
        loadWholeFile(filePath, fileSize)?.let {
            binding.pdfViewer.loadPdf(ByteArrayInputStream(it), fileName, fileSize)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        binding.pdfViewer.onCreateOptionMenu(menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        return binding.pdfViewer.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return binding.pdfViewer.onOptionsItemSelected(item) || super.onOptionsItemSelected(item)
    }
}
