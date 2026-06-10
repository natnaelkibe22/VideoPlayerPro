package com.natkibe.videoplayerpro.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.model.VideoFolderSummary

class FolderAdapter(
    private var items: List<VideoFolderSummary>,
    private val onClick: (VideoFolderSummary) -> Unit,
    private val showThumbnails: Boolean = false,
    private val thumbnailBitmapProvider: ((String) -> android.graphics.Bitmap?)? = null,
    private val onThumbnailMissing: ((String) -> Unit)? = null
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
        // Use folder glyph as placeholder (keep folder icon look)
        holder.thumb.text = "📁"
        holder.title.text = item.folderName
        holder.subtitle.text = "${item.videoCount} videos • ${item.storageRoot}"
        holder.itemView.contentDescription = "Folder row"
        holder.itemView.setOnClickListener { onClick(item) }

        if (showThumbnails && thumbnailBitmapProvider != null && item.previewUri != null) {
            holder.thumbImage.visibility = android.view.View.VISIBLE
            holder.thumbImage.setImageResource(R.drawable.ic_playlist)
            holder.thumbImage.tag = item.previewUri
            val bitmap = thumbnailBitmapProvider.invoke(item.previewUri)
            if (bitmap != null) {
                holder.thumbImage.setImageBitmap(bitmap)
                holder.thumb.visibility = android.view.View.GONE
            } else {
                holder.thumb.visibility = android.view.View.VISIBLE
                onThumbnailMissing?.invoke(item.previewUri)
            }
        } else {
            holder.thumbImage.setImageDrawable(null)
            holder.thumbImage.visibility = android.view.View.GONE
            holder.thumb.visibility = android.view.View.VISIBLE
        }
    }

    override fun getItemCount(): Int = items.size

    class FolderViewHolder(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val thumb: TextView = root.findViewById(R.id.videoThumb)
        val title: TextView = root.findViewById(R.id.videoTitle)
        val subtitle: TextView = root.findViewById(R.id.videoSubtitle)
        val thumbImage: ImageView = root.findViewById(R.id.videoThumbImage)
    }
}
