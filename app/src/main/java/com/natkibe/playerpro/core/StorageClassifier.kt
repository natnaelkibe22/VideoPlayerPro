package com.natkibe.playerpro.core

object StorageClassifier {
    fun classify(relativePath: String?, volumeName: String? = null): String {
        val path = relativePath.orEmpty()
        val volume = volumeName.orEmpty()
        return when {
            volume.contains("external", ignoreCase = true) || volume.contains("usb", ignoreCase = true) -> "USB/SD"
            path.contains("Movies", ignoreCase = true) -> "System/Movies"
            path.contains("Download", ignoreCase = true) -> "System/Downloads"
            path.contains("DCIM", ignoreCase = true) -> "System/Camera"
            path.isBlank() -> "System/Internal"
            else -> path.substringBefore('/').ifBlank { "System/Internal" }
        }
    }
}
