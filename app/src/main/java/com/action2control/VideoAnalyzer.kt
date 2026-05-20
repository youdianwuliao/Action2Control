package com.action2control

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.action2control.ml.ActionClassifier
import com.action2control.ml.ActionSequenceExtractor
import com.action2control.ml.FrameExtractor
import com.action2control.ml.PoseEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分析视频文件，返回动作序列
 *
 * 流程:
 * 1. 提取视频帧
 * 2. 姿态估计 (MediaPipe Pose Landmarker)
 * 3. 动作分类 (TFLite)
 */
suspend fun analyzeVideo(
    context: Context,
    videoPath: String,
    onProgress: (current: Int, total: Int, phase: String) -> Unit
): List<String> = withContext(Dispatchers.Default) {
    val frameExtractor = FrameExtractor(context)
    val poseEstimator = PoseEstimator(context)
    val actionClassifier = ActionClassifier(context)
    var frames: List<Bitmap> = emptyList()

    try {
        // 步骤 1: 加载动作分类模型
        onProgress(0, 100, "加载分类模型")
        val modelLoaded = actionClassifier.loadModel()
        if (!modelLoaded) {
            Log.w("analyzeVideo", "TFLite model not found, all frames will be classified as 'unknown'")
        }

        // 步骤 2: 初始化姿态估计器
        onProgress(0, 100, "初始化姿态估计")
        val poseInitialized = poseEstimator.initialize()
        if (!poseInitialized) {
            Log.e("analyzeVideo", "PoseLandmarker initialization failed")
            return@withContext emptyList<String>()
        }

        // 步骤 3: 提取视频帧
        onProgress(0, 100, "提取视频帧")
        frames = frameExtractor.extractFrames(videoPath)
        if (frames.isEmpty()) {
            Log.e("analyzeVideo", "No frames extracted from video")
            return@withContext emptyList<String>()
        }
        Log.i("analyzeVideo", "Extracted ${frames.size} frames")

        // 步骤 4: 姿态估计
        onProgress(0, frames.size, "姿态估计")
        val allLandmarks = poseEstimator.estimatePoses(frames) { current, total ->
            onProgress(current, total, "姿态估计")
        }

        // 步骤 5: 动作分类
        onProgress(0, allLandmarks.size, "动作分类")
        val rawActions = actionClassifier.classifyAll(allLandmarks)

        Log.i("analyzeVideo", "Raw classification: ${rawActions.size} actions")
        Log.d("analyzeVideo", ActionSequenceExtractor.getStatistics(rawActions))

        // 步骤 6: 动作序列去抖和提取
        onProgress(0, 100, "动作序列提取")
        val deduplicated = ActionSequenceExtractor.extractSequence(rawActions, minFrames = 5)

        Log.i("analyzeVideo", "Final action sequence: $deduplicated")

        deduplicated
    } finally {
        // 释放所有资源
        poseEstimator.close()
        actionClassifier.close()
        frames.forEach { it.recycle() }
    }
}
