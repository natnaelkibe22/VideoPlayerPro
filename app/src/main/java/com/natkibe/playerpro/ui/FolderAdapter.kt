package com.natkibe.playerpro.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.playerpro.R
import com.natkibe.playerpro.model.VideoFolderSummary

class FolderAdapter(
    private var items: List<VideoFolderSummary>,
    private val onClick: (VideoFolderSummary) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    fun submit(newItems: List<VideoFolderSummary>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_video, parent, false)
        return FolderViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val item = items[position]
        holder.thumb.text = "📁"
        holder.title.text = item.folderName
        holder.subtitle.text = "${item.videoCount} videos • ${item.storageRoot}"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class FolderViewHolder(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val thumb: TextView = root.findViewById(R.id.videoThumb)
        val title: TextView = root.findViewById(R.id.videoTitle)
        val subtitle: TextView = root.findViewById(R.id.videoSubtitle)
    }
}
