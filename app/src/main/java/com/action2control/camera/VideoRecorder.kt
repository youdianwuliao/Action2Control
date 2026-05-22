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
    private var mediaProjection: android.media.projection.MediaProjection? = null

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
        if (isRecording) {
            Log.w(TAG, "Already recording, ignoring start request")
            return false
        }

        return try {
            Log.d(TAG, "Starting screen recording")

            // 创建输出文件
            val outputDir = context.getExternalFilesDir(null) ?: run {
                onRecordingError("无法获取存储目录")
                return false
            }

            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            currentVideoFile = File(outputDir, "screen_${System.currentTimeMillis()}.mp4")
            Log.d(TAG, "Output file: ${currentVideoFile!!.absolutePath}")

            // 获取屏幕尺寸
            val displayMetrics = getDisplayMetrics()
            Log.d(TAG, "Screen size: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}, density: ${displayMetrics.densityDpi}")

            // 获取 MediaProjection
            mediaProjection = getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                onRecordingError("无法获取 MediaProjection")
                return false
            }

            // 配置 MediaRecorder
            mediaRecorder = createMediaRecorder(
                outputFile = currentVideoFile!!,
                width = displayMetrics.widthPixels,
                height = displayMetrics.heightPixels
            )

            // 创建 VirtualDisplay
            createVirtualDisplay(displayMetrics)

            // 开始录制
            mediaRecorder?.start()

            isRecording = true
            isPaused = false

            Log.d(TAG, "Screen recording started successfully")
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
        if (!isRecording) {
            Log.w(TAG, "Not recording, ignoring stop request")
            return
        }

        Log.d(TAG, "Stopping recording")

        var stopException: Exception? = null
        try {
            // 如果处于暂停状态，先恢复再停止
            if (isPaused) {
                Log.d(TAG, "Resuming before stop")
                mediaRecorder?.resume()
                isPaused = false
            }

            // 停止录制
            mediaRecorder?.stop()
            Log.d(TAG, "MediaRecorder stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording (often happens if recording is too short)", e)
            stopException = e
        }

        val path = currentVideoFile?.absolutePath ?: ""
        val file = currentVideoFile
        
        // 即使 stop() 报错，也要检查文件是否有效 (某些设备 stop 会抛异常但文件是完整的)
        val fileSize = file?.length() ?: 0
        val fileValid = file?.exists() == true && fileSize > 1024 // 至少 1KB
        
        if (stopException == null) {
            // 正常停止
            Log.d(TAG, "Video saved to: $path")
            onRecordingComplete(path)
        } else if (fileValid) {
            // stop 报错但文件有效，尝试继续分析
            Log.w(TAG, "stop() threw exception but file is valid ($path, size: $fileSize), proceeding to analysis")
            onRecordingComplete(path)
        } else {
            // stop 报错且文件无效
            Log.e(TAG, "Stop failed and file is invalid or empty")
            onRecordingError("停止录制失败: ${stopException.message}")
        }

        // 清理状态
        isRecording = false
        isPaused = false
        cleanup()
    }

    /**
     * 暂停录制
     */
    fun pauseRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording, cannot pause")
            return
        }
        if (isPaused) {
            Log.w(TAG, "Already paused")
            return
        }

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
        if (!isRecording) {
            Log.w(TAG, "Not recording, cannot resume")
            return
        }
        if (!isPaused) {
            Log.w(TAG, "Not paused, cannot resume")
            return
        }

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
        Log.d(TAG, "Releasing resources")
        if (isRecording) {
            stopRecording()
        }
        cleanup()
    }

    private fun cleanup() {
        Log.d(TAG, "Cleaning up")
        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null

        isRecording = false
        isPaused = false
    }

    private fun createMediaRecorder(outputFile: File, width: Int, height: Int): MediaRecorder {
        Log.d(TAG, "Creating MediaRecorder (video only, skipping audio)")
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+
            MediaRecorder(context).apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile.absolutePath)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                // 跳过音频录制：屏幕动作分析不需要声音，且需要运行时权限
                Log.d(TAG, "Audio recording disabled (not needed for screen action analysis)")
                prepare()
            }
        } else {
            // API 24-30
            @Suppress("DEPRECATION")
            MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile.absolutePath)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                // 跳过音频录制
                Log.d(TAG, "Audio recording disabled (not needed for screen action analysis)")
                prepare()
            }
        }
    }

    private fun createVirtualDisplay(displayMetrics: DisplayMetrics) {
        val projection = mediaProjection ?: run {
            onRecordingError("MediaProjection 未初始化")
            return
        }

        val surface = mediaRecorder?.surface ?: run {
            onRecordingError("MediaRecorder surface 不可用")
            return
        }

        virtualDisplay = MediaProjectionHelper.createVirtualDisplay(projection, displayMetrics, surface)
        Log.d(TAG, "VirtualDisplay created: ${virtualDisplay != null}")
    }

    private fun getMediaProjection(
        resultCode: Int,
        data: android.content.Intent
    ): android.media.projection.MediaProjection? {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as? android.media.projection.MediaProjectionManager

        return try {
            val projection = projectionManager?.getMediaProjection(resultCode, data)
            Log.d(TAG, "MediaProjection obtained: ${projection != null}")
            projection
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MediaProjection", e)
            null
        }
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = windowManager.currentWindowMetrics?.bounds
            if (display != null) {
                DisplayMetrics().apply {
                    widthPixels = display.width()
                    heightPixels = display.height()
                    densityDpi = context.resources.displayMetrics.densityDpi
                }
            } else {
                // Fallback to default display metrics
                context.resources.displayMetrics
            }
        } else {
            @Suppress("DEPRECATION")
            DisplayMetrics().also { windowManager.defaultDisplay?.getMetrics(it) ?: run {
                // Fallback to resources
                context.resources.displayMetrics
            }}
        }
    }
}
