package sushi.hardcore.droidfs.adapters

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import coil3.Image
import coil3.ImageLoader
import coil3.imageLoader
import coil3.load
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFramePercent
import sushi.hardcore.droidfs.FileTypes
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.filesystems.EncryptedFileReaderFileSystem
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.PathUtils
import java.text.DateFormat
import java.util.Locale

class ExplorerElementAdapter(
    val activity: AppCompatActivity,
    val encryptedVolume: EncryptedVolume?,
    private val listener: Listener,
) : SelectableAdapter<ExplorerElement>(listener::onSelectionChanged) {
    val dateFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.getDefault())
    var explorerElements = listOf<ExplorerElement>()
    @SuppressLint("NotifyDataSetChanged")
    set(value) {
        field = value
        notifyDataSetChanged()
    }
    var isUsingListLayout = true
    private var thumbnailsLoader: ImageLoader? = null
    var loadThumbnails = true
    private var iconImage: Image? = null
    private var iconVideo: Image? = null

    init {
        if (encryptedVolume != null) {
            activity.imageLoader.enqueue(ImageRequest.Builder(activity).data(R.drawable.icon_file_image).target { result -> iconImage = result}.build())
            activity.imageLoader.enqueue(ImageRequest.Builder(activity).data(R.drawable.icon_file_video).target { result -> iconVideo = result}.build())
            thumbnailsLoader = ImageLoader.Builder(activity).diskCache(null).fileSystem(EncryptedFileReaderFileSystem(encryptedVolume)).components {
                add(VideoFrameDecoder.Factory())
            }.build()
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

        open fun bind(explorerElement: ExplorerElement, position: Int) {
            textElementName.text = explorerElement.name
            (bindingAdapter as ExplorerElementAdapter?)?.setSelectable(itemView, position)
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
        private var thumbnailLoadingTask: Disposable? = null

        fun cancelThumbnailLoading() {
            thumbnailLoadingTask?.dispose()
        }

        private fun setThumbnailOrDefaultIcon(fullPath: String, defaultIconId: Int, placeholder: Image?): Disposable {
            val adapter = (bindingAdapter as ExplorerElementAdapter?)!!
            return if (adapter.loadThumbnails && adapter.thumbnailsLoader != null) {
                icon.load(fullPath, adapter.thumbnailsLoader!!) {
                    videoFramePercent(0.1)
                    placeholder(placeholder)
                }
            } else {
                icon.load(defaultIconId)
            }
        }

        override fun bind(explorerElement: ExplorerElement, position: Int, isSelected: Boolean) {
            super.bind(explorerElement, position, isSelected)
            val adapter = bindingAdapter as ExplorerElementAdapter
            thumbnailLoadingTask = when {
                FileTypes.isImage(explorerElement.name) -> {
                    setThumbnailOrDefaultIcon(explorerElement.fullPath, R.drawable.icon_file_image, adapter.iconImage)
                }
                FileTypes.isVideo(explorerElement.name) -> {
                    setThumbnailOrDefaultIcon(explorerElement.fullPath, R.drawable.icon_file_video, adapter.iconVideo)
                }
                else -> icon.load(
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
            holder.cancelThumbnailLoading()
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