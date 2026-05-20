package com.action2control.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 视频录制器
 * 基于 CameraX VideoCapture API 实现视频录制
 */
class VideoRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: androidx.camera.view.PreviewView,
    private val onRecordingComplete: (videoPath: String) -> Unit,
    private val onRecordingError: (error: String) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var isRecording = false

    companion object {
        private const val TAG = "VideoRecorder"
    }

    val isRecordingState: Boolean
        get() = isRecording

    /**
     * 初始化 CameraProvider 并绑定用例
     */
    suspend fun initialize(): Boolean {
        return try {
            val provider = getCameraProvider(context)
            cameraProvider = provider
            bindCameraUseCases()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Camera initialization failed", e)
            onRecordingError("相机初始化失败: ${e.message}")
            false
        }
    }

    /**
     * 开始录制视频
     */
    fun startRecording() {
        if (isRecording) return

        val capture = videoCapture ?: run {
            onRecordingError("相机未初始化")
            return
        }

        val outputDir = context.getExternalFilesDir(null) ?: run {
            onRecordingError("无法获取存储目录")
            return
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val videoFile = File(outputDir, "video_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        activeRecording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context), recordingEventsListener)

        isRecording = true
        Log.d(TAG, "Recording started: ${videoFile.absolutePath}")
    }

    /**
     * 停止录制视频
     */
    fun stopRecording() {
        if (!isRecording) return
        activeRecording?.stop()
        activeRecording = null
        isRecording = false
        Log.d(TAG, "Recording stopped")
    }

    /**
     * 释放资源
     */
    fun release() {
        stopRecording()
        cameraProvider?.unbindAll()
        cameraProvider = null
        videoCapture = null
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()

        videoCapture = VideoCapture.Builder(recorder).build()

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(videoCapture!!)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                useCaseGroup
            )
            Log.d(TAG, "Camera use cases bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
            onRecordingError("相机绑定失败: ${e.message}")
        }
    }

    private val recordingEventsListener = Consumer<VideoRecordEvent> { event ->
        when (event) {
            is VideoRecordEvent.Start -> {
                Log.d(TAG, "Recording started event")
            }
            is VideoRecordEvent.Finalize -> {
                isRecording = false
                if (event.hasError()) {
                    Log.e(TAG, "Recording error: ${event.error}")
                    onRecordingError("录制失败: ${getErrorMessage(event.error)}")
                } else {
                    val path = event.outputResults.outputUri.path ?: ""
                    Log.d(TAG, "Recording finalized: $path")
                    onRecordingComplete(path)
                }
            }
            else -> {}
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            VideoRecordEvent.Finalize.ERROR_UNKNOWN -> "未知错误"
            VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "无有效数据"
            VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "编码失败"
            else -> "录制错误 ($errorCode)"
        }
    }
}

/**
 * 获取 CameraProvider (suspend 版本)
 */
private suspend fun getCameraProvider(context: Context): ProcessCameraProvider {
    val future = ProcessCameraProvider.getInstance(context)
    return suspendCoroutine { continuation ->
        future.addListener({
            try {
                continuation.resume(future.get())
            } catch (e: Exception) {
                continuation.resumeWith(Result.failure(e))
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
