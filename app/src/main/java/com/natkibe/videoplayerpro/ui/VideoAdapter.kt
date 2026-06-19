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
import com.natkibe.videoplayerpro.core.ResolutionUtil
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

        // Subtitle as metadata tertiary
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

        // Quality badges on thumbnail
        val badgeInfo = ResolutionUtil.parseBadges(item.resolution, item.frameRate)
        if (badgeInfo.badges.isNotEmpty() && showThumbnails) {
            holder.qualityBadgeContainer.visibility = View.VISIBLE
            holder.qualityBadgeContainer.removeAllViews()
            for (badge in badgeInfo.badges) {
                val chip = createBadgeChip(holder.qualityBadgeContainer, badge)
                holder.qualityBadgeContainer.addView(chip)
            }
        } else {
            holder.qualityBadgeContainer.visibility = View.GONE
        }

        // Default placeholder
        holder.thumb.text = if (showThumbnails) "▣" else "▶"

        // Load actual thumbnail if enabled and provider available
        if (showThumbnails && thumbnailBitmapProvider != null) {
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
        if (onLongPress != null) {
            holder.itemView.setOnLongClickListener {
                onLongPress.invoke(item)
                true
            }
        }
    }

    private fun createBadgeChip(container: ViewGroup, text: String): TextView {
        val chip = TextView(container.context).apply {
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            textSize = 9f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(0xBB2F80ED.toInt())
            setPadding(4, 1, 4, 1)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = if (container.childCount > 0) 2 else 0
            }
        }
        return chip
    }

    override fun getItemCount(): Int = items.size

    class VideoViewHolder(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val thumb: TextView = root.findViewById(R.id.videoThumb)
        val thumbImage: ImageView = root.findViewById(R.id.videoThumbImage)
        val title: TextView = root.findViewById(R.id.videoTitle)
        val subtitle: TextView = root.findViewById(R.id.videoSubtitle)
        val durationBadge: TextView = root.findViewById(R.id.durationBadge)
        val qualityBadgeContainer: LinearLayout = root.findViewById(R.id.qualityBadgeContainer)
        val thumbnailContainer: FrameLayout = root.findViewById(R.id.thumbnailContainer)
    }
}
