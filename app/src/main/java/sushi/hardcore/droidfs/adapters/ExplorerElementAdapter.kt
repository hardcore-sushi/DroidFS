package sushi.hardcore.droidfs.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sushi.hardcore.droidfs.ConstValues.Companion.getAssociatedDrawable
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.util.PathUtils
import java.text.DateFormat
import java.util.*

class ExplorerElementAdapter(
    context: Context,
    private val onExplorerElementClick: (Int) -> Unit,
    private val onExplorerElementLongClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.getDefault())
    var explorerElements = listOf<ExplorerElement>()
        set(value) {
            field = value
            unSelectAll()
        }
    private val inflater: LayoutInflater = LayoutInflater.from(context)
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

    open class ExplorerElementViewHolder(
        itemView: View,
        private val onClick: (Int) -> Boolean,
        private val onLongClick: (Int) -> Boolean,
    ) : RecyclerView.ViewHolder(itemView) {
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
            selectableContainer.setOnClickListener {
                setBackground(onClick(position))
            }
            selectableContainer.setOnLongClickListener {
                setBackground(onLongClick(position))
                true
            }
        }
    }

    open class RegularElementViewHolder(
        itemView: View,
        private val dateFormat: DateFormat,
        onClick: (Int) -> Boolean,
        onLongClick: (Int) -> Boolean,
    ) : ExplorerElementViewHolder(itemView, onClick, onLongClick) {
        open fun bind(explorerElement: ExplorerElement, position: Int, isSelected: Boolean) {
            super.bind(explorerElement, position)
            textElementSize.text = PathUtils.formatSize(explorerElement.size)
            textElementMtime.text = dateFormat.format(explorerElement.mTime)
            setBackground(isSelected)
        }
    }

    class FileViewHolder(
        itemView: View,
        dateFormat: DateFormat,
        onClick: (Int) -> Boolean,
        onLongClick: (Int) -> Boolean,
    ) : RegularElementViewHolder(itemView, dateFormat, onClick, onLongClick) {
        override fun bind(explorerElement: ExplorerElement, position: Int, isSelected: Boolean) {
            super.bind(explorerElement, position, isSelected)
            icon.setImageResource(getAssociatedDrawable(explorerElement.name))
        }
    }
    class DirectoryViewHolder(
        itemView: View,
        dateFormat: DateFormat,
        onClick: (Int) -> Boolean,
        onLongClick: (Int) -> Boolean,
    ) : RegularElementViewHolder(itemView, dateFormat, onClick, onLongClick) {
        override fun bind(explorerElement: ExplorerElement, position: Int, isSelected: Boolean) {
            super.bind(explorerElement, position, isSelected)
            icon.setImageResource(R.drawable.icon_folder)
        }
    }
    class ParentFolderViewHolder(
        itemView: View,
        onClick: (Int) -> Boolean,
        onLongClick: (Int) -> Boolean,
    ): ExplorerElementViewHolder(itemView, onClick, onLongClick) {
        override fun bind(explorerElement: ExplorerElement, position: Int) {
            super.bind(explorerElement, position)
            textElementSize.text = ""
            textElementMtime.setText(R.string.parent_folder)
            icon.setImageResource(R.drawable.icon_folder)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = inflater.inflate(R.layout.adapter_explorer_element, parent, false)
        return when (viewType) {
            ExplorerElement.REGULAR_FILE_TYPE -> FileViewHolder(view, dateFormat, ::onItemClick, ::onItemLongClick)
            ExplorerElement.DIRECTORY_TYPE -> DirectoryViewHolder(view, dateFormat, ::onItemClick, ::onItemLongClick)
            ExplorerElement.PARENT_FOLDER_TYPE -> ParentFolderViewHolder(view, ::onItemClick, ::onItemLongClick)
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