package com.action2control.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 姿态估计器
 * 使用 MediaPipe Pose Landmarker 对视频帧进行逐帧姿态估计
 */
class PoseEstimator(private val context: Context) {

    companion object {
        private const val TAG = "PoseEstimator"
        private const val MODEL_PATH = "pose_landmarker.task"
        private const val NUM_POSES = 1
        private const val MIN_POSE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_POSE_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
    }

    private var poseLandmarker: PoseLandmarker? = null
    private var isInitialized = false

    /**
     * 初始化 PoseLandmarker
     * 使用 CPU 模式，异步加载模型
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseOptions = com.google.mediapipe.tasks.core.BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .build()

            val options = com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions.builder()
                .setRunningMode(RunningMode.IMAGE)
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
                .setMinPosePresenceConfidence(MIN_POSE_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setNumPoses(NUM_POSES)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            isInitialized = true
            Log.i(TAG, "PoseLandmarker initialized successfully")
            true
        } catch (e: IllegalStateException) {
            Log.e(TAG, "PoseLandmarker initialization failed: model not found", e)
            isInitialized = false
            false
        } catch (e: RuntimeException) {
            Log.e(TAG, "PoseLandmarker initialization failed", e)
            isInitialized = false
            false
        }
    }

    /**
     * 对帧列表进行姿态估计
     *
     * @param frames 输入 Bitmap 列表
     * @param onProgress 进度回调 (current, total)
     * @return 每帧的关键点列表，每帧 33 个 NormalizedLandmark
     */
    suspend fun estimatePoses(
        frames: List<Bitmap>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<List<NormalizedLandmark>> = withContext(Dispatchers.Default) {
        val landmarker = poseLandmarker
        if (!isInitialized || landmarker == null) {
            Log.e(TAG, "PoseLandmarker not initialized, returning empty list")
            return@withContext emptyList<List<NormalizedLandmark>>()
        }

        val allLandmarks = mutableListOf<List<NormalizedLandmark>>()
        val totalFrames = frames.size

        frames.forEachIndexed { index, bitmap ->
            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result: PoseLandmarkerResult = landmarker.detect(mpImage)

                val landmarks = result.landmarks().firstOrNull() ?: emptyList()
                allLandmarks.add(landmarks)

                onProgress?.invoke(index + 1, totalFrames)
                Log.d(TAG, "Processed frame ${index + 1}/$totalFrames, landmarks: ${landmarks.size}")
            } catch (e: RuntimeException) {
                Log.e(TAG, "Error detecting pose for frame ${index + 1}", e)
                allLandmarks.add(emptyList())
                onProgress?.invoke(index + 1, totalFrames)
            }
        }

        Log.i(TAG, "Pose estimation completed: ${allLandmarks.size} frames processed")
        allLandmarks
    }

    /**
     * 释放资源
     */
    fun close() {
        try {
            poseLandmarker?.close()
            poseLandmarker = null
            isInitialized = false
            Log.i(TAG, "PoseLandmarker resources released")
        } catch (e: RuntimeException) {
            Log.e(TAG, "Error releasing PoseLandmarker resources", e)
        }
    }

    /**
     * 检查是否已初始化
     */
    fun isReady(): Boolean = isInitialized && poseLandmarker != null
}
