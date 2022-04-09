package sushi.hardcore.droidfs.adapters

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
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
    val onExplorerElementLongClick: (Int) -> Unit,
    val thumbnailMaxSize: Long,
) : SelectableAdapter<ExplorerElement>() {
    val dateFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.getDefault())
    var explorerElements = listOf<ExplorerElement>()
    @SuppressLint("NotifyDataSetChanged")
    set(value) {
        field = value
        thumbnailsCache?.evictAll()
        notifyDataSetChanged()
    }
    var isUsingListLayout = true
    private var thumbnailsCache: LruCache<String, Bitmap>? = null

    init {
        if (gocryptfsVolume != null) {
            thumbnailsCache = LruCache((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt())
        }
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
        onExplorerElementClick(position)
        return super.onItemClick(position)
    }

    override fun onItemLongClick(position: Int): Boolean {
        onExplorerElementLongClick(position)
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
            textElementSize.text = PathUtils.formatSize(explorerElement.size)
            (bindingAdapter as ExplorerElementAdapter?)?.let {
                textElementMtime.text = it.dateFormat.format(explorerElement.mTime)
            }
        }
    }

    class FileViewHolder(itemView: View) : RegularElementViewHolder(itemView) {
        var displayThumbnail = true
        var target: DrawableImageViewTarget? = null

        private fun loadThumbnail(fullPath: String, adapter: ExplorerElementAdapter) {
            adapter.gocryptfsVolume?.let { volume ->
                displayThumbnail = true
                Thread {
                    volume.loadWholeFile(fullPath, maxSize = adapter.thumbnailMaxSize).first?.let {
                        if (displayThumbnail) {
                            adapter.activity.runOnUiThread {
                                if (displayThumbnail && !adapter.activity.isFinishing) {
                                    target = Glide.with(adapter.activity).load(it).skipMemoryCache(true).into(object : DrawableImageViewTarget(icon) {
                                        override fun onResourceReady(
                                            resource: Drawable,
                                            transition: Transition<in Drawable>?
                                        ) {
                                            val bitmap = resource.toBitmap()
                                            adapter.thumbnailsCache!!.put(fullPath, bitmap.copy(bitmap.config, true))
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

        private fun setThumbnailOrDefaultIcon(fullPath: String, defaultIconId: Int) {
            var setDefaultIcon = true
            (bindingAdapter as ExplorerElementAdapter?)?.let { adapter ->
                adapter.thumbnailsCache?.let {
                    val thumbnail = it.get(fullPath)
                    if (thumbnail != null) {
                        icon.setImageBitmap(thumbnail)
                        setDefaultIcon = false
                    } else {
                        loadThumbnail(fullPath, adapter)
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
                ConstValues.isImage(explorerElement.name) -> {
                    setThumbnailOrDefaultIcon(explorerElement.fullPath, R.drawable.icon_file_image)
                }
                ConstValues.isVideo(explorerElement.name) -> {
                    setThumbnailOrDefaultIcon(explorerElement.fullPath, R.drawable.icon_file_video)
                }
                else -> icon.setImageResource(
                    when {
                        ConstValues.isText(explorerElement.name) -> R.drawable.icon_file_text
                        ConstValues.isPDF(explorerElement.name) -> R.drawable.icon_file_pdf
                        ConstValues.isAudio(explorerElement.name) -> R.drawable.icon_file_audio
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
            //cancel pending thumbnail display
            holder.displayThumbnail = false
            holder.target?.let {
                Glide.with(activity).clear(it)
            }
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