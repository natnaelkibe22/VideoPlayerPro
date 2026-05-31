package com.natkibe.videoplayerpro.core

object StorageClassifier {
    /**
     * Classifies a video's storage location into a human-readable category.
     *
     * - USB/SD: Removable storage volumes (usb, usbotg, or non-primary external volumes).
     * - System/Movies: Videos under a "Movies" directory.
     * - System/Downloads: Videos under a "Download" or "Downloads" directory.
     * - System/Camera: Videos under a "DCIM" directory.
     * - System/Internal: Everything else on internal/primary storage.
     */
    fun classify(relativePath: String?, volumeName: String? = null): String {
        val path = relativePath.orEmpty()
        val volume = volumeName.orEmpty()

        // Blank path with no volume info → internal
        if (path.isBlank() && volume.isBlank()) return "System/Internal"

        // USB/SD detection: explicit "usb" in volume name, or external volume that is NOT primary
        val isUsbOrSd = volume.contains("usb", ignoreCase = true) ||
            (volume.contains("external", ignoreCase = true) &&
             !volume.contains("primary", ignoreCase = true))

        if (isUsbOrSd) return "USB/SD"

        // Classify by path segments
        return when {
            path.contains("Movies", ignoreCase = true) -> "System/Movies"
            path.contains("Download", ignoreCase = true) -> "System/Downloads"
            path.contains("DCIM", ignoreCase = true) -> "System/Camera"
            path.isBlank() -> "System/Internal"
            else -> path.substringBefore('/').ifBlank { "System/Internal" }
        }
    }
}
