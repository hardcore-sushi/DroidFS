package sushi.hardcore.droidfs.adapters

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import sushi.hardcore.droidfs.R

abstract class SelectableAdapter<T> : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var selectedItems: MutableSet<Int> = HashSet()

    protected abstract fun getItems(): List<T>

    override fun getItemCount(): Int {
        return getItems().size
    }

    protected open fun toggleSelection(position: Int): Boolean {
        return if (selectedItems.contains(position)) {
            selectedItems.remove(position)
            false
        } else {
            selectedItems.add(position)
            true
        }
    }

    protected open fun onItemClick(position: Int): Boolean {
        if (selectedItems.isNotEmpty()) {
            return toggleSelection(position)
        }
        return false
    }

    protected open fun onItemLongClick(position: Int): Boolean {
        return toggleSelection(position)
    }

    protected open fun isSelectable(position: Int): Boolean {
        return true
    }

    fun selectAll() {
        for (i in getItems().indices) {
            if (!selectedItems.contains(i) && isSelectable(i)) {
                selectedItems.add(i)
                notifyItemChanged(i)
            }
        }
    }

    fun unSelectAll(notifyChange: Boolean) {
        if (notifyChange) {
            val whatWasSelected = selectedItems
            selectedItems = HashSet()
            whatWasSelected.forEach {
                notifyItemChanged(it)
            }
        } else {
            selectedItems.clear()
        }
    }

    private fun setBackground(rootView: View, isSelected: Boolean) {
        rootView.setBackgroundResource(if (isSelected) R.color.itemSelected else 0)
    }

    protected fun setSelectable(element: View, rootView: View, position: Int) {
        element.setOnClickListener {
            setBackground(rootView, onItemClick(position))
        }
        element.setOnLongClickListener {
            setBackground(rootView, onItemLongClick(position))
            true
        }
        setBackground(rootView, selectedItems.contains(position))
    }
}