package com.natkibe.videoplayerpro.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.core.TimeFormat
import com.natkibe.videoplayerpro.data.VideoItemEntity

/**
 * Compact adapter for floating folder panel with minimal UI footprint.
 * Uses row_video_floating_compact.xml for space-constrained 180dp width layout.
 */
class FloatingVideoAdapter(
    private var items: List<VideoItemEntity>,
    private val onClick: (VideoItemEntity) -> Unit,
    private val thumbnailBitmapProvider: ((String) -> Bitmap?)? = null,
    private val onThumbnailMissing: ((String) -> Unit)? = null
) : RecyclerView.Adapter<FloatingVideoAdapter.FloatingVideoViewHolder>() {

    fun submit(newItems: List<VideoItemEntity>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    fun notifyUriChanged(uri: String) {
        val index = items.indexOfFirst { it.uri == uri }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FloatingVideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_video_floating_compact, parent, false)
        return FloatingVideoViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: FloatingVideoViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.displayName

        // Subtitle as compact metadata
        val folderInfo = item.folderName.takeIf { it.isNotBlank() } ?: "Videos"
        val durationStr = if (item.durationMs > 0L) TimeFormat.duration(item.durationMs) else ""
        holder.subtitle.text = if (durationStr.isNotBlank()) {
            "$folderInfo • $durationStr"
        } else {
            folderInfo
        }

        // Duration badge on thumbnail
        holder.durationBadge.text = if (item.durationMs > 0L) {
            TimeFormat.duration(item.durationMs)
        } else {
            ""
        }
        holder.durationBadge.visibility = if (item.durationMs > 0L) View.VISIBLE else View.GONE

        // Default placeholder
        holder.thumb.text = "▶"

        // Load actual thumbnail if provider available
        if (thumbnailBitmapProvider != null) {
            holder.thumbImage.tag = item.uri
            val bmp = thumbnailBitmapProvider.invoke(item.uri)
            if (bmp != null) {
                holder.thumbImage.setImageBitmap(bmp)
                holder.thumbImage.visibility = View.VISIBLE
                holder.thumb.visibility = View.GONE
            } else {
                holder.thumbImage.setImageDrawable(null)
                holder.thumbImage.visibility = View.GONE
                holder.thumb.visibility = View.VISIBLE
                onThumbnailMissing?.invoke(item.uri)
            }
        } else {
            holder.thumbImage.visibility = View.GONE
            holder.thumb.visibility = View.VISIBLE
        }

        holder.itemView.contentDescription = "Video row"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class FloatingVideoViewHolder(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val thumb: TextView = root.findViewById(R.id.videoThumb)
        val thumbImage: ImageView = root.findViewById(R.id.videoThumbImage)
        val title: TextView = root.findViewById(R.id.videoTitle)
        val subtitle: TextView = root.findViewById(R.id.videoSubtitle)
        val durationBadge: TextView = root.findViewById(R.id.durationBadge)
        val thumbnailContainer: FrameLayout = root.findViewById(R.id.thumbnailContainer)
    }
}
