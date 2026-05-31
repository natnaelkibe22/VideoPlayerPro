package com.natkibe.videoplayerpro.model

/**
 * Storage categories matching [com.natkibe.videoplayerpro.core.StorageClassifier] output.
 */
enum class StorageTab(val label: String) {
    ALL("All"),
    INTERNAL("System/Internal"),
    USB_OR_SD("USB/SD"),
    MOVIES("System/Movies"),
    DOWNLOADS("System/Downloads"),
    CAMERA("System/Camera")
}
