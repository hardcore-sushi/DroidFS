package sushi.hardcore.droidfs.adapters

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.Transition
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.GocryptfsVolume
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.util.PathUtils
import java.text.DateFormat
import java.util.*

class ExplorerElementAdapter(
    val activity: AppCompatActivity,
    val gocryptfsVolume: GocryptfsVolume?,
    val onExplorerElementClick: (Int) -> Unit,
    val onExplorerElementLongClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val dateFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.getDefault())
    var explorerElements = listOf<ExplorerElement>()
    val selectedItems: MutableList<Int> = ArrayList()

    override fun getItemCount(): Int {
        return explorerElements.size
    }

    private fun toggleSelection(position: Int): Boolean {
        if (!explorerElements[position].isParentFolder) {
            if (selectedItems.contains(position)) {
                selectedItems.remove(position)
            } else {
                selectedItems.add(position)
                return true
            }
        }
        return false
    }

    private fun onItemClick(position: Int): Boolean {
        onExplorerElementClick(position)
        if (selectedItems.isNotEmpty()) {
            return toggleSelection(position)
        }
        return false
    }

    private fun onItemLongClick(position: Int): Boolean {
        onExplorerElementLongClick(position)
        return toggleSelection(position)
    }

    fun selectAll() {
        for (i in explorerElements.indices) {
            if (!selectedItems.contains(i) && !explorerElements[i].isParentFolder) {
                selectedItems.add(i)
            }
        }
        notifyDataSetChanged()
    }

    fun unSelectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    open class ExplorerElementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textElementName by lazy {
            itemView.findViewById<TextView>(R.id.text_element_name)
        }
        protected val textElementSize: TextView by lazy {
            itemView.findViewById(R.id.text_element_size)
        }
        protected val textElementMtime: TextView by lazy {
            itemView.findViewById(R.id.text_element_mtime)
        }
        protected val icon: ImageView by lazy {
            itemView.findViewById(R.id.icon_element)
        }
        private val selectableContainer: LinearLayout by lazy {
            itemView.findViewById(R.id.selectable_container)
        }

        protected fun setBackground(isSelected: Boolean) {
            itemView.setBackgroundResource(if (isSelected) { R.color.itemSelected } else { 0 })
        }

        open fun bind(explorerElement: ExplorerElement, position: Int) {
            textElementName.text = explorerElement.name
            (bindingAdapter as ExplorerElementAdapter?)?.let { adapter ->
                selectableContainer.setOnClickListener {
                    setBackground(adapter.onItemClick(position))
                }
                selectableContainer.setOnLongClickListener {
                    setBackground(adapter.onItemLongClick(position))
                    true
                }
            }
        }
    }

    open class RegularElementViewHolder(itemView: View) : ExplorerElementViewHolder(itemView) {
        open fun bind(explorerElement: ExplorerElement, position: Int, isSelected: Boolean) {
            super.bind(explorerElement, position)
            textElementSize.text = PathUtils.formatSize(explorerElement.size)
            (bindingAdapter as ExplorerElementAdapter?)?.let {
                textElementMtime.text = it.dateFormat.format(explorerElement.mTime)
            }
            setBackground(isSelected)
        }
    }

    class FileViewHolder(itemView: View) : RegularElementViewHolder(itemView) {
        var displayThumbnail = true
        var target: DrawableImageViewTarget? = null

        private fun loadThumbnail(fullPath: String) {
            (bindingAdapter as ExplorerElementAdapter?)?.let { adapter ->
                adapter.gocryptfsVolume?.let { volume ->
                    displayThumbnail = true
                    Thread {
                        volume.loadWholeFile(fullPath, maxSize = 50_000_000).first?.let {
                            if (displayThumbnail) {
                                adapter.activity.runOnUiThread {
                                    if (displayThumbnail) {
                                        target = Glide.with(adapter.activity).load(it).into(object : DrawableImageViewTarget(icon) {
                                            override fun onResourceReady(
                                                resource: Drawable,
                                                transition: Transition<in Drawable>?
                                            ) {
                                                super.onResourceReady(resource, transition)
                                                target = null
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }.start()
                }
            }
        }
        override fun bind(explorerElement: ExplorerElement, position: Int, isSelected: Boolean) {
            super.bind(explorerElement, position, isSelected)
            icon.setImageResource(
                when {
                    ConstValues.isImage(explorerElement.name) -> {
                        loadThumbnail(explorerElement.fullPath)
                        R.drawable.icon_file_image
                    }
                    ConstValues.isVideo(explorerElement.name) -> {
                        loadThumbnail(explorerElement.fullPath)
                        R.drawable.icon_file_video
                    }
                    ConstValues.isText(explorerElement.name) -> R.drawable.icon_file_text
                    ConstValues.isPDF(explorerElement.name) -> R.drawable.icon_file_pdf
                    ConstValues.isAudio(explorerElement.name) -> R.drawable.icon_file_audio
                    else -> R.drawable.icon_file_unknown
                }
            )
        }
    }

    class DirectoryViewHolder(itemView: View) : RegularElementViewHolder(itemView) {
        override fun bind(explorerElement: ExplorerElement, position: Int, isSelected: Boolean) {
            super.bind(explorerElement, position, isSelected)
            icon.setImageResource(R.drawable.icon_folder)
        }
    }

    class ParentFolderViewHolder(itemView: View): ExplorerElementViewHolder(itemView) {
        override fun bind(explorerElement: ExplorerElement, position: Int) {
            super.bind(explorerElement, position)
            textElementSize.text = ""
            textElementMtime.setText(R.string.parent_folder)
            icon.setImageResource(R.drawable.icon_folder)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is FileViewHolder) {
            //cancel pending thumbnail display
            holder.displayThumbnail = false
            holder.target?.let {
                Glide.with(activity).clear(it)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = activity.layoutInflater.inflate(R.layout.adapter_explorer_element, parent, false)
        return when (viewType) {
            ExplorerElement.REGULAR_FILE_TYPE -> FileViewHolder(view)
            ExplorerElement.DIRECTORY_TYPE -> DirectoryViewHolder(view)
            ExplorerElement.PARENT_FOLDER_TYPE -> ParentFolderViewHolder(view)
            else -> throw IllegalArgumentException()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val element = explorerElements[position]
        if (element.isParentFolder) {
            (holder as ParentFolderViewHolder).bind(element, position)
        } else {
            (holder as RegularElementViewHolder).bind(element, position, selectedItems.contains(position))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return explorerElements[position].elementType.toInt()
    }
}