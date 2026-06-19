package com.natkibe.videoplayerpro.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight smart thumbnail selector that picks the best frame from a video
 * by analyzing brightness, contrast, and sharpness at 3 candidate positions.
 *
 * No AI, no face/object detection, no heavy processing. Uses pixel-level
 * analysis on downscaled frames to keep CPU and memory usage low.
 */
class SmartThumbnailSelector(private val context: Context) {

    /**
     * Selects the best thumbnail frame from the given video URI.
     * Returns null if no acceptable frame is found (fallback to frame at 0ms).
     */
    suspend fun selectBestFrame(
        videoUri: String,
        targetWidth: Int = 320,
        targetHeight: Int = 180
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val source = Uri.parse(videoUri)
            val retriever = MediaMetadataRetriever().apply {
                setDataSource(context, source)
            }

            // Get duration in microseconds
            val durationUs = try {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                    ?.times(1000L) ?: return@withContext null
            } catch (_: Exception) {
                return@withContext null
            }

            if (durationUs <= 0) {
                // Fallback to frame at 0
                val fallback = retriever.frameAtTime
                retriever.release()
                return@withContext scaleBitmap(fallback, targetWidth, targetHeight)
            }

            // Candidate frame positions: 15%, 35%, 60% of duration
            val candidates = longArrayOf(
                (durationUs * 0.15).toLong(),
                (durationUs * 0.35).toLong(),
                (durationUs * 0.60).toLong()
            )

            var bestFrame: Bitmap? = null
            var bestScore = -1f

            for (timeUs in candidates) {
                val frame = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Exception) {
                    null
                }

                if (frame == null) continue

                // Downscale for analysis (keep memory low)
                val smallFrame = scaleBitmap(frame, 80, 45)
                if (smallFrame != null && smallFrame != frame) {
                    frame.recycle()
                }
                val analysis = smallFrame ?: continue

                val score = evaluateFrame(analysis)
                if (smallFrame != frame) {
                    analysis.recycle()
                }

                if (score > bestScore) {
                    bestScore = score
                    // Get a properly scaled version for the actual thumbnail
                    val fullFrame = try {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (_: Exception) {
                        null
                    }
                    bestFrame?.recycle()
                    bestFrame = scaleBitmap(fullFrame, targetWidth, targetHeight)
                    fullFrame?.recycle()
                }
            }

            retriever.release()

            // If no candidate passed, fallback to frame at 0
            if (bestFrame == null) {
                val fallback = try {
                    MediaMetadataRetriever().apply {
                        setDataSource(context, source)
                    }.frameAtTime
                } catch (_: Exception) {
                    null
                }
                return@withContext scaleBitmap(fallback, targetWidth, targetHeight)?.also {
                    fallback?.recycle()
                }
            }

            bestFrame
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Evaluates a frame and returns a quality score.
     * Higher score = better thumbnail candidate.
     * Returns -1f for rejected frames (black, extremely dark, blurry).
     */
    private fun evaluateFrame(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val totalPixels = pixels.size
        var sumLuma = 0L
        var blackPixels = 0
        val lumaValues = IntArray(totalPixels)

        // First pass: collect luma values
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Perceived luminance (ITU-R BT.601)
            val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            lumaValues[i] = luma
            sumLuma += luma
            if (luma < 15) blackPixels++
        }

        // Reject if more than 60% of pixels are black
        val blackRatio = blackPixels.toFloat() / totalPixels
        if (blackRatio > 0.6f) return -1f

        val meanLuma = sumLuma.toFloat() / totalPixels

        // Reject if extremely dark (mean luma < 25)
        if (meanLuma < 25f) return -1f

        // Second pass: calculate contrast (standard deviation) and sharpness
        var varianceSum = 0f
        var sharpnessSum = 0f

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val luma = lumaValues[idx]

                // Contrast contribution
                val diff = luma - meanLuma
                varianceSum += diff * diff

                // Sharpness: sum of absolute horizontal and vertical gradients
                val left = lumaValues[y * width + (x - 1)]
                val right = lumaValues[y * width + (x + 1)]
                val top = lumaValues[(y - 1) * width + x]
                val bottom = lumaValues[(y + 1) * width + x]
                val gradX = kotlin.math.abs(right - left)
                val gradY = kotlin.math.abs(bottom - top)
                sharpnessSum += gradX + gradY
            }
        }

        val pixelCount = (width - 2) * (height - 2)
        val contrast = kotlin.math.sqrt(varianceSum / pixelCount)
        val sharpness = sharpnessSum / pixelCount

        // Reject if blurry (low sharpness)
        if (sharpness < 3.0f) return -1f

        // Reject if very low contrast
        if (contrast < 15f) return -1f

        // Composite score: balance between brightness, contrast, and sharpness
        // Brightness near middle (80-180) is preferred (not too dark, not overexposed)
        val brightnessScore = when {
            meanLuma < 80f -> meanLuma / 80f * 0.3f
            meanLuma > 180f -> (255f - meanLuma) / 75f * 0.3f
            else -> 0.3f
        }
        val contrastScore = (contrast / 70f).coerceIn(0f, 0.4f)
        val sharpnessScore = (sharpness / 20f).coerceIn(0f, 0.4f)
        // Penalize excessive black pixels
        val blackPenalty = (1f - blackRatio).coerceIn(0.5f, 1f)

        return (brightnessScore + contrastScore + sharpnessScore) * blackPenalty
    }

    private fun scaleBitmap(bitmap: Bitmap?, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (bitmap == null) return null
        if (bitmap.width <= targetWidth && bitmap.height <= targetHeight) return bitmap
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
