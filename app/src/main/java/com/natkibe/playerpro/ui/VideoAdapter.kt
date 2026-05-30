package com.natkibe.playerpro.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.playerpro.R
import com.natkibe.playerpro.core.TimeFormat
import com.natkibe.playerpro.data.VideoItemEntity

class VideoAdapter(
    private var items: List<VideoItemEntity>,
    private var showThumbnails: Boolean,
    private val onClick: (VideoItemEntity) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    fun submit(newItems: List<VideoItemEntity>, showThumbnails: Boolean) {
        this.items = newItems
        this.showThumbnails = showThumbnails
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_video, parent, false)
        return VideoViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.displayName
        holder.subtitle.text = "${item.folderName} • ${TimeFormat.duration(item.durationMs)} • ${item.storageRoot}"
        holder.thumb.text = if (showThumbnails) "▣" else "▶"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class VideoViewHolder(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val thumb: TextView = root.findViewById(R.id.videoThumb)
        val title: TextView = root.findViewById(R.id.videoTitle)
        val subtitle: TextView = root.findViewById(R.id.videoSubtitle)
    }
}
