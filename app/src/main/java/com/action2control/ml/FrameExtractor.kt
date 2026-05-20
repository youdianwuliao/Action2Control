package com.action2control.ml

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 帧提取器
 * 从视频文件中按时间间隔提取 Bitmap 帧
 */
class FrameExtractor(private val context: Context) {

    companion object {
        private const val TAG = "FrameExtractor"
        private const val DEFAULT_FRAME_INTERVAL_US = 500_000L // 0.5 秒
    }

    /**
     * 从视频文件提取帧
     *
     * @param videoPath 视频文件路径
     * @param frameIntervalUs 帧提取间隔（微秒），默认 500ms
     * @param maxFrames 最大提取帧数，防止内存溢出，默认 100
     * @return 提取的 Bitmap 列表
     */
    suspend fun extractFrames(
        videoPath: String,
        frameIntervalUs: Long = DEFAULT_FRAME_INTERVAL_US,
        maxFrames: Int = 100
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()

        try {
            retriever.setDataSource(videoPath)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationUs = durationStr?.toLongOrNull()?.times(1000) ?: 0L

            if (durationUs <= 0L) {
                Log.e(TAG, "Invalid video duration: $durationStr")
                return@withContext emptyList<Bitmap>()
            }

            var currentTimeUs = 0L
            var frameCount = 0

            while (currentTimeUs < durationUs && frameCount < maxFrames) {
                val frame = retriever.getFrameAtTime(
                    currentTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )

                if (frame != null) {
                    val scaledFrame = Bitmap.createScaledBitmap(frame, 640, 480, true)
                    if (scaledFrame != frame) {
                        frame.recycle()
                    }
                    frames.add(scaledFrame)
                    frameCount++
                    Log.d(TAG, "Extracted frame $frameCount at ${currentTimeUs / 1000}ms")
                }

                currentTimeUs += frameIntervalUs
            }

            Log.i(TAG, "Extracted ${frames.size} frames from video (${durationUs / 1000}ms)")
            frames
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid video data source: $videoPath", e)
            emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "IO error extracting frames from: $videoPath", e)
            emptyList()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Runtime error extracting frames from: $videoPath", e)
            emptyList()
        } finally {
            try {
                retriever.release()
            } catch (e: RuntimeException) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }
    }
}
