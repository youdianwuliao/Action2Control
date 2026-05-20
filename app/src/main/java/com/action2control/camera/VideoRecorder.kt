package com.action2control.camera

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File

/**
 * 屏幕录制器
 * 基于 MediaProjection + MediaRecorder 实现屏幕录制
 * 支持暂停/恢复功能
 */
class ScreenRecorder(
    private val context: Context,
    private val onRecordingComplete: (videoPath: String) -> Unit,
    private val onRecordingError: (error: String) -> Unit
) {

    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false
    private var isPaused = false
    private var currentVideoFile: File? = null

    companion object {
        private const val TAG = "ScreenRecorder"
        private const val VIDEO_FRAME_RATE = 30
        private const val VIDEO_BIT_RATE = 4_000_000 // 4Mbps
    }

    val isRecordingState: Boolean
        get() = isRecording

    val isPausedState: Boolean
        get() = isPaused

    /**
     * 开始录制屏幕
     */
    fun startRecording(resultCode: Int, data: android.content.Intent): Boolean {
        if (isRecording) return false

        return try {
            // 创建输出文件
            val outputDir = context.getExternalFilesDir(null) ?: run {
                onRecordingError("无法获取存储目录")
                return false
            }

            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            currentVideoFile = File(outputDir, "screen_${System.currentTimeMillis()}.mp4")

            // 获取屏幕尺寸
            val displayMetrics = getDisplayMetrics()

            // 配置 MediaRecorder
            mediaRecorder = createMediaRecorder(
                outputFile = currentVideoFile!!,
                width = displayMetrics.widthPixels,
                height = displayMetrics.heightPixels
            )

            // 创建 VirtualDisplay
            createVirtualDisplay(resultCode, data, displayMetrics)

            mediaRecorder?.start()

            isRecording = true
            isPaused = false

            Log.d(TAG, "Screen recording started: ${currentVideoFile!!.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            onRecordingError("录制启动失败: ${e.message}")
            cleanup()
            false
        }
    }

    /**
     * 停止录制
     */
    fun stopRecording() {
        if (!isRecording) return

        try {
            if (isPaused) {
                // 如果处于暂停状态，先恢复再停止
                mediaRecorder?.resume()
                isPaused = false
            }

            mediaRecorder?.stop()
            Log.d(TAG, "Screen recording stopped")

            val path = currentVideoFile?.absolutePath ?: ""
            isRecording = false
            isPaused = false

            onRecordingComplete(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            onRecordingError("停止录制失败: ${e.message}")
        } finally {
            cleanup()
        }
    }

    /**
     * 暂停录制
     */
    fun pauseRecording() {
        if (!isRecording || isPaused) return

        try {
            mediaRecorder?.pause()
            isPaused = true
            Log.d(TAG, "Recording paused")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause recording", e)
            onRecordingError("暂停录制失败: ${e.message}")
        }
    }

    /**
     * 恢复录制
     */
    fun resumeRecording() {
        if (!isRecording || !isPaused) return

        try {
            mediaRecorder?.resume()
            isPaused = false
            Log.d(TAG, "Recording resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume recording", e)
            onRecordingError("恢复录制失败: ${e.message}")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        if (isRecording) {
            stopRecording()
        }
        cleanup()
    }

    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null

        mediaRecorder?.release()
        mediaRecorder = null

        isRecording = false
        isPaused = false
    }

    private fun createMediaRecorder(outputFile: File, width: Int, height: Int): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+
            MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile.absolutePath)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }
        } else {
            // API 24-30
            @Suppress("DEPRECATION")
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile.absolutePath)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }
        }
    }

    private fun createVirtualDisplay(
        resultCode: Int,
        data: android.content.Intent,
        displayMetrics: DisplayMetrics
    ) {
        val mediaProjection = getMediaProjection(resultCode, data) ?: run {
            onRecordingError("无法获取 MediaProjection")
            return
        }

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenRecorder",
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            mediaRecorder?.surface,
            null,
            null
        )

        Log.d(TAG, "VirtualDisplay created")
    }

    private fun getMediaProjection(
        resultCode: Int,
        data: android.content.Intent
    ): android.media.projection.MediaProjection? {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as? android.media.projection.MediaProjectionManager

        return projectionManager?.getMediaProjection(resultCode, data)
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = context.display ?: windowManager.defaultDisplay
            DisplayMetrics().also { display.getRealMetrics(it) }
        } else {
            @Suppress("DEPRECATION")
            DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
        }
    }
}
