package com.natkibe.videoplayerpro.playlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.core.TimeFormat

/**
 * RecyclerView adapter for the playlist drawer items.
 * Uses Coil for thumbnail loading and supports showing/hiding thumbnails.
 * Communicates item selection via [listener].
 */
class PlaylistItemAdapter(
    private var items: List<PlaylistItemUiModel> = emptyList(),
    private var showThumbnails: Boolean = true,
    private val listener: PlaylistInteractionListener
) : RecyclerView.Adapter<PlaylistItemAdapter.ViewHolder>() {

    /** Updates the item list and optionally thumbnail visibility, then rebinds visible rows. */
    fun submit(newItems: List<PlaylistItemUiModel>, showThumbnails: Boolean) {
        this.items = newItems
        this.showThumbnails = showThumbnails
        notifyDataSetChanged()
    }

    /** Returns the item at the given position, for external navigation logic. */
    fun getItemAt(position: Int): PlaylistItemUiModel? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_playlist_item, parent, false)
        return ViewHolder(view as FrameLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val root: FrameLayout) : RecyclerView.ViewHolder(root) {

        private val playingIndicator: View = root.findViewById(R.id.playingIndicator)
        private val thumbnail: ImageView = root.findViewById(R.id.itemThumbnail)
        private val title: TextView = root.findViewById(R.id.itemTitle)
        private val duration: TextView = root.findViewById(R.id.itemDuration)
        private val folderName: TextView = root.findViewById(R.id.itemFolderName)
        private val resumeProgress: ProgressBar = root.findViewById(R.id.resumeProgress)

        fun bind(item: PlaylistItemUiModel) {
            // Playing indicator (left blue bar)
            playingIndicator.isVisible = item.isCurrentlyPlaying

            // Row background highlight for currently playing item
            root.setBackgroundColor(
                if (item.isCurrentlyPlaying) {
                    root.context.getColor(R.color.surface_dark_light)
                } else {
                    root.context.getColor(android.R.color.transparent)
                }
            )

            // Thumbnail
            if (showThumbnails && item.thumbnailUri != null) {
                thumbnail.isVisible = true
                thumbnail.load(item.thumbnailUri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_play)
                    error(R.drawable.ic_play)
                }
            } else {
                thumbnail.isVisible = false
            }

            // Title
            title.text = item.title

            // Duration
            duration.text = if (item.durationMs > 0L) {
                TimeFormat.duration(item.durationMs)
            } else {
                ""
            }

            // Folder name
            if (item.folderName != null) {
                folderName.isVisible = true
                folderName.text = item.folderName
            } else {
                folderName.isVisible = false
            }

            // Resume progress bar
            if (item.resumePositionMs > 0L && item.durationMs > 0L) {
                resumeProgress.isVisible = true
                val progress = ((item.resumePositionMs.toFloat() / item.durationMs) * 1000f).toInt()
                resumeProgress.progress = progress.coerceIn(0, 1000)
            } else {
                resumeProgress.isVisible = false
            }

            // Click listener
            root.setOnClickListener { listener.onVideoSelected(item.uri) }
        }
    }
}
