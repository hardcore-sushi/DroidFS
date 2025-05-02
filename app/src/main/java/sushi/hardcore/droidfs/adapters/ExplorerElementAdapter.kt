package sushi.hardcore.droidfs.adapters

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import sushi.hardcore.droidfs.FileTypes
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.ThumbnailsLoader
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.PathUtils
import java.text.DateFormat
import java.util.*

class ExplorerElementAdapter(
    val activity: AppCompatActivity,
    val encryptedVolume: EncryptedVolume?,
    private val listener: Listener,
    thumbnailMaxSize: Long,
) : SelectableAdapter<ExplorerElement>(listener::onSelectionChanged) {
    val dateFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.getDefault())
    var explorerElements = listOf<ExplorerElement>()
    @SuppressLint("NotifyDataSetChanged")
    set(value) {
        field = value
        thumbnailsCache?.evictAll()
        notifyDataSetChanged()
    }
    var isUsingListLayout = true
    private var thumbnailsLoader: ThumbnailsLoader? = null
    private var thumbnailsCache: LruCache<String, Bitmap>? = null
    var loadThumbnails = true

    init {
        if (encryptedVolume != null) {
            thumbnailsLoader = ThumbnailsLoader(activity, encryptedVolume, thumbnailMaxSize, activity.lifecycleScope).apply {
                initialize()
            }
            thumbnailsCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 4).toInt()) {
                override fun sizeOf(key: String, value: Bitmap) = value.byteCount
            }
        }
    }

    interface Listener {
        fun onSelectionChanged(size: Int)
        fun onExplorerElementClick(position: Int)
        fun onExplorerElementLongClick(position: Int)
    }

    override fun getItems(): List<ExplorerElement> {
        return explorerElements
    }

    override fun toggleSelection(position: Int): Boolean {
        return if (!explorerElements[position].isParentFolder) {
            super.toggleSelection(position)
        } else {
            false
        }
    }

    override fun onItemClick(position: Int): Boolean {
        listener.onExplorerElementClick(position)
        return super.onItemClick(position)
    }

    override fun onItemLongClick(position: Int): Boolean {
        listener.onExplorerElementLongClick(position)
        return super.onItemLongClick(position)
    }

    override fun isSelectable(position: Int): Boolean {
        return !explorerElements[position].isParentFolder
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

        open fun bind(explorerElement: ExplorerElement, position: Int) {
            textElementName.text = explorerElement.name
            (bindingAdapter as ExplorerElementAdapter?)?.setSelectable(selectableContainer, itemView, position)
        }
    }

    open class RegularElementViewHolder(itemView: View) : ExplorerElementViewHolder(itemView) {
        open fun bind(explorerElement: ExplorerElement, position: Int, isSelected: Boolean) {
            super.bind(explorerElement, position)
            textElementSize.text = PathUtils.formatSize(explorerElement.stat.size)
            (bindingAdapter as ExplorerElementAdapter?)?.let {
                textElementMtime.text = it.dateFormat.format(explorerElement.stat.mTime)
            }
        }
    }

    class FileViewHolder(itemView: View) : RegularElementViewHolder(itemView) {
        private var task = -1

        fun cancelThumbnailLoading(adapter: ExplorerElementAdapter) {
            if (task != -1) {
                adapter.thumbnailsLoader?.cancel(task)
            }
        }

        private fun setThumbnailOrDefaultIcon(fullPath: String, defaultIconId: Int) {
            var setDefaultIcon = true
            (bindingAdapter as ExplorerElementAdapter?)?.let { adapter ->
                adapter.thumbnailsCache?.let {
                    val thumbnail = it.get(fullPath)
                    if (thumbnail != null) {
                        icon.setImageBitmap(thumbnail)
                        setDefaultIcon = false
                    } else if (adapter.loadThumbnails) {
                        task = adapter.thumbnailsLoader!!.loadAsync(fullPath, icon) { resource ->
                            val bitmap = resource.toBitmap()
                            adapter.thumbnailsCache!!.put(fullPath, bitmap.copy(bitmap.config!!, true))
                        }
                    }
                }
            }
            if (setDefaultIcon) {
                icon.setImageResource(defaultIconId)
            }
        }

        override fun bind(explorerElement: ExplorerElement, position: Int, isSelected: Boolean) {
            super.bind(explorerElement, position, isSelected)
            when {
                FileTypes.isImage(explorerElement.name) -> {
                    setThumbnailOrDefaultIcon(explorerElement.fullPath, R.drawable.icon_file_image)
                }
                FileTypes.isVideo(explorerElement.name) -> {
                    setThumbnailOrDefaultIcon(explorerElement.fullPath, R.drawable.icon_file_video)
                }
                else -> icon.setImageResource(
                    when {
                        FileTypes.isText(explorerElement.name) -> R.drawable.icon_file_text
                        FileTypes.isPDF(explorerElement.name) -> R.drawable.icon_file_pdf
                        FileTypes.isAudio(explorerElement.name) -> R.drawable.icon_file_audio
                        else -> R.drawable.icon_file_unknown
                    }
                )
            }
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
            holder.cancelThumbnailLoading(this)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = activity.layoutInflater.inflate(
            if (isUsingListLayout) {
                R.layout.adapter_explorer_element_list
            } else {
                R.layout.adapter_explorer_element_grid
            }, parent, false
        )
        return when (viewType) {
            Stat.S_IFREG -> FileViewHolder(view)
            Stat.S_IFDIR -> DirectoryViewHolder(view)
            Stat.PARENT_FOLDER_TYPE -> ParentFolderViewHolder(view)
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
        return explorerElements[position].stat.type
    }
}