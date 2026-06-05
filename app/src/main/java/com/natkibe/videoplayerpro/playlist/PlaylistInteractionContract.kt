package com.natkibe.videoplayerpro.playlist

/**
 * Commands emitted by the playlist package when the user interacts with the drawer.
 * The host (PlayerActivity) processes these to control the player engine.
 */
sealed interface PlaylistInteractionCommand {

    /** User tapped a video in the playlist. */
    data class OnVideoSelected(val uri: String) : PlaylistInteractionCommand

    /** Drawer finished opening animation. */
    data object OnDrawerOpen : PlaylistInteractionCommand

    /** Drawer finished closing animation. */
    data object OnDrawerClose : PlaylistInteractionCommand

    /** User tapped the toggle button (open if closed, close if open). */
    data object OnToggleDrawer : PlaylistInteractionCommand
}

/**
 * Callback interface through which the playlist package communicates outward.
 * The host activity implements this to bridge to PlayerEngine.
 * No PlayerEngine/ExoPlayer references exist inside the playlist package.
 */
interface PlaylistInteractionListener {

    /** A video item was selected in the playlist. */
    fun onVideoSelected(uri: String)

    /** Drawer has finished opening. */
    fun onDrawerOpen()

    /** Drawer has finished closing. */
    fun onDrawerClose()

    /** Toggle the drawer open/closed (from external button press). */
    fun onToggleDrawer()
}
