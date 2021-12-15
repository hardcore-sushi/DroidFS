package sushi.hardcore.droidfs.file_viewers

import android.util.Log
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import sushi.hardcore.droidfs.databinding.ActivityPdfViewerBinding
import java.io.File

class PdfViewer : FileViewerActivity() {

    private lateinit var binding: ActivityPdfViewerBinding
    private var pageNumber = 0

    override fun hideSystemUi() {
        //don't hide system ui
    }

    override fun getFileType(): String {
        return "pdf"
    }

    override fun viewFile() {
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadPdfFile()
    }

    private fun loadPdfFile() {
        loadWholeFile(filePath)?.let {
            binding.pdfViewer.fromBytes(it)
                .defaultPage(pageNumber)
                .onPageChange { page, pageCount ->
                    pageNumber = page; title = String.format(
                    "%s %s / %s",
                    File(filePath).name,
                    page + 1,
                    pageCount
                )
                }
                .enableAnnotationRendering(true)
                // .scrollHandle(DefaultScrollHandle(this))
                .spacing(10) // in dp
                .onPageError { page, t -> Log.e("PdfViewer", "Cannot load page $page", t); }
                .load()

        }
    }

}