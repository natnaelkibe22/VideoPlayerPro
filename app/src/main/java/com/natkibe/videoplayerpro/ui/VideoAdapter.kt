package com.natkibe.videoplayerpro.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.core.TimeFormat
import com.natkibe.videoplayerpro.data.VideoItemEntity

class VideoAdapter(
    private var items: List<VideoItemEntity>,
    private var showThumbnails: Boolean,
    private val onClick: (VideoItemEntity) -> Unit,
    private val onLongPress: ((VideoItemEntity) -> Unit)? = null,
    private val thumbnailBitmapProvider: ((String) -> Bitmap?)? = null,
    private val onThumbnailMissing: ((String) -> Unit)? = null
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    fun submit(newItems: List<VideoItemEntity>, showThumbnails: Boolean) {
        this.items = newItems
        this.showThumbnails = showThumbnails
        notifyDataSetChanged()
    }

    /** Returns the item at the given position, used by PlayerActivity for playlist navigation. */
    fun getItemAt(position: Int): VideoItemEntity? = items.getOrNull(position)

    fun notifyUriChanged(uri: String) {
        val index = items.indexOfFirst { it.uri == uri }
        if (index >= 0) notifyItemChanged(index)
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

        // Load actual thumbnail if enabled and provider available
        if (showThumbnails && thumbnailBitmapProvider != null) {
            val bmp = thumbnailBitmapProvider.invoke(item.uri)
            if (bmp != null) {
                holder.thumbImage.setImageBitmap(bmp)
                holder.thumbImage.visibility = android.view.View.VISIBLE
                holder.thumb.visibility = android.view.View.GONE
            } else {
                holder.thumbImage.setImageDrawable(null)
                holder.thumbImage.visibility = android.view.View.GONE
                holder.thumb.visibility = android.view.View.VISIBLE
                onThumbnailMissing?.invoke(item.uri)
            }
        } else {
            holder.thumbImage.visibility = android.view.View.GONE
            holder.thumb.visibility = android.view.View.VISIBLE
        }

        holder.itemView.contentDescription = "Video row"
        holder.itemView.setOnClickListener { onClick(item) }
        if (onLongPress != null) {
            holder.itemView.setOnLongClickListener {
                onLongPress.invoke(item)
                true
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class VideoViewHolder(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val thumb: TextView = root.findViewById(R.id.videoThumb)
        val thumbImage: ImageView = root.findViewById(R.id.videoThumbImage)
        val title: TextView = root.findViewById(R.id.videoTitle)
        val subtitle: TextView = root.findViewById(R.id.videoSubtitle)
    }
}
