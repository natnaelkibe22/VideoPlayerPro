package com.natkibe.videoplayerpro.player

import androidx.media3.common.PlaybackException

object PlayerErrorMapper {
    fun mapError(error: PlaybackException): PlayerErrorInfo {
        val userMessage = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                "File access error: USB/SD card may have been removed or file is corrupted"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "Network connection failed. Check your connection and try again"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network timed out. Check your connection and try again"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Video file not found. It may have been moved or deleted"
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
                "Cannot read this video file. The file may be corrupted"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "This video file is malformed or uses an unsupported container format"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "This video file uses an unsupported container format"
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "The video manifest is malformed"
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                "The video manifest format is not supported"
            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "Cannot decode this video: unsupported codec, 4K/HEVC limit, or corrupt file"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "Codec initialization failed. The video format may not be supported on this device"
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                "No suitable decoder found for this video format"
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ->
                "Audio track initialization failed"
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
                "Audio playback failed"
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED ->
                "DRM error: This content may be protected"
            else -> "Playback error occurred (code: ${error.errorCode})"
        }

        val isRecoverable = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> false
            else -> true
        }

        return PlayerErrorInfo(
            errorCode = error.errorCode,
            userMessage = userMessage,
            isRecoverable = isRecoverable
        )
    }
}
