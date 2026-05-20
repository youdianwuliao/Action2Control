package com.action2control.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * 动作分类器
 * 使用 TensorFlow Lite 模型对姿态关键点进行分类
 *
 * 输入形状: [1, 99]  (33 关键点 × 3 坐标 [x, y, z])
 * 输出形状: [1, 5]  (5 种动作概率)
 * 标签映射: swipe_up, swipe_down, like, back, unknown
 */
class ActionClassifier(private val context: Context) {

    companion object {
        private const val TAG = "ActionClassifier"
        private const val MODEL_FILE = "action_model.tflite"
        private const val NUM_KEYPOINTS = 33
        private const val NUM_COORDS_PER_KEYPOINT = 3 // x, y, z
        private const val INPUT_SIZE = NUM_KEYPOINTS * NUM_COORDS_PER_KEYPOINT // 99
        private const val NUM_CLASSES = 5

        private val LABELS = arrayOf(
            "swipe_up",
            "swipe_down",
            "like",
            "back",
            "unknown"
        )
    }

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    /**
     * 从 assets 加载 TFLite 模型
     */
    fun loadModel(): Boolean {
        return try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            }
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            Log.i(TAG, "TFLite model loaded successfully: $MODEL_FILE")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load TFLite model: $MODEL_FILE, will return 'unknown' for classification", e)
            isModelLoaded = false
            false
        }
    }

    /**
     * 对姿态关键点进行分类
     *
     * @param landmarks 姿态关键点列表 (应为 33 个点)
     * @return 最高概率的动作标签
     */
    fun classify(landmarks: List<NormalizedLandmark>): String {
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "Model not loaded, returning 'unknown'")
            return LABELS.last()
        }

        try {
            val inputArray = normalizeLandmarks(landmarks)
            val inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            for (value in inputArray) {
                inputBuffer.putFloat(value)
            }
            inputBuffer.rewind()

            val outputBuffer = TensorBuffer.createFixedSize(
                intArrayOf(1, NUM_CLASSES),
                org.tensorflow.lite.DataType.FLOAT32
            )

            val outputs = mapOf(0 to outputBuffer.buffer)

            interpreter?.run(inputBuffer, outputs[0])

            val probabilities = outputBuffer.floatArray
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0

            val predictedLabel = LABELS[maxIndex]
            val confidence = probabilities[maxIndex]

            Log.d(TAG, "Classification result: $predictedLabel (confidence: ${String.format("%.4f", confidence)})")
            return predictedLabel
        } catch (e: RuntimeException) {
            Log.e(TAG, "Error during classification", e)
            return LABELS.last()
        }
    }

    /**
     * 对一组关键点进行分类 (批量处理)
     *
     * @param allLandmarks 每帧的关键点列表
     * @return 每帧对应的动作标签列表
     */
    fun classifyAll(allLandmarks: List<List<NormalizedLandmark>>): List<String> {
        return allLandmarks.map { landmarks ->
            classify(landmarks)
        }
    }

    /**
     * 将 NormalizedLandmark 列表转换为归一化的浮点数组
     * 确保输出长度为 99 (33 × 3)
     */
    private fun normalizeLandmarks(landmarks: List<NormalizedLandmark>): FloatArray {
        val result = FloatArray(INPUT_SIZE)

        for (i in 0 until NUM_KEYPOINTS) {
            val landmark = if (i < landmarks.size) landmarks[i] else null

            val baseIndex = i * NUM_COORDS_PER_KEYPOINT
            result[baseIndex] = landmark?.x() ?: 0.0f
            result[baseIndex + 1] = landmark?.y() ?: 0.0f
            result[baseIndex + 2] = landmark?.z() ?: 0.0f
        }

        return result
    }

    /**
     * 从 assets 目录加载模型文件到 ByteBuffer
     */
    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val channel = inputStream.channel

        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * 释放资源
     */
    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            isModelLoaded = false
            Log.i(TAG, "TFLite Interpreter released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TFLite Interpreter", e)
        }
    }

    /**
     * 检查模型是否已加载
     */
    fun isReady(): Boolean = isModelLoaded && interpreter != null
}
