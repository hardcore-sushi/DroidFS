package sushi.hardcore.droidfs.file_viewers

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File

class TextEditor: FileViewerActivity() {
    private lateinit var fileName: String
    private lateinit var editor: EditText
    private var changedSinceLastSave = false
    private var wordWrap = true

    override fun getFileType(): String {
        return "text"
    }

    override fun viewFile() {
        fileName = File(fileViewerViewModel.filePath!!).name
        title = fileName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        loadWholeFile(fileViewerViewModel.filePath!!) {
            try {
                loadLayout(String(it))
                onBackPressedDispatcher.addCallback(this) {
                    checkSaveAndExit()
                }
            } catch (e: OutOfMemoryError){
                CustomAlertDialogBuilder(this, theme)
                    .setTitle(R.string.error)
                    .setMessage(R.string.outofmemoryerror_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok) { _, _ -> goBackToExplorer()}
                    .show()
            }
        }
    }
    private fun loadLayout(fileContent: String){
        if (wordWrap){
            setContentView(R.layout.activity_text_editor_wrap)
        } else {
            setContentView(R.layout.activity_text_editor)
        }
        editor = findViewById(R.id.text_editor)
        editor.setText(fileContent)
        editor.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!changedSinceLastSave){
                    changedSinceLastSave = true
                    @SuppressLint("SetTextI18n")
                    title = "*$fileName"
                }
            }
        })
    }
    private fun save(): Boolean{
        var success = false
        val content = editor.text.toString().toByteArray()
        val fileHandle = encryptedVolume.openFileWriteMode(fileViewerViewModel.filePath!!)
        if (fileHandle != -1L) {
            var offset: Long = 0
            while (offset < content.size && encryptedVolume.write(fileHandle, offset, content, offset, content.size.toLong()).also { offset += it } > 0) {}
            if (offset == content.size.toLong()){
                success = encryptedVolume.truncate(fileViewerViewModel.filePath!!, offset)
            }
            encryptedVolume.closeFile(fileHandle)
        }
        if (success){
            Toast.makeText(this, getString(R.string.file_saved), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
        }
        return success
    }

    private fun checkSaveAndExit(){
        if (changedSinceLastSave){
            CustomAlertDialogBuilder(this, theme)
                .setTitle(R.string.warning)
                .setMessage(R.string.ask_save)
                .setPositiveButton(R.string.save) { _, _ ->
                    if (save()){
                        goBackToExplorer()
                    }
                }
                .setNegativeButton(R.string.discard){ _, _ -> goBackToExplorer()}
                .show()
        } else {
            goBackToExplorer()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.text_editor, menu)
        menu.findItem(R.id.word_wrap).isChecked = wordWrap
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId){
            android.R.id.home -> {
                checkSaveAndExit()
            }
            R.id.menu_save -> {
                if (save()){
                    changedSinceLastSave = false
                    title = fileName
                }
            }
            R.id.word_wrap -> {
                wordWrap = !item.isChecked
                loadLayout(editor.text.toString())
                invalidateOptionsMenu()
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }
}
