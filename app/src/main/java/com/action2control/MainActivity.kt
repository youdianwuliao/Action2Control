package com.action2control

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.action2control.ml.ActionClassifier
import com.action2control.ml.ActionSequenceExtractor
import com.action2control.ml.FrameExtractor
import com.action2control.ml.PoseEstimator
import com.action2control.service.ControlAccessibilityService
import com.action2control.ui.CameraScreen
import com.action2control.ui.MainScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主 Activity
 * 负责页面导航和主流程控制
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AppContent(
                    onOpenSettings = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

/**
 * 应用内容容器
 * 管理页面状态和导航
 */
@Composable
fun AppContent(
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var showCameraScreen by remember { mutableStateOf(false) }
    var actionSequence by remember { mutableStateOf<List<String>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableStateOf("") }
    var hasVideo by remember { mutableStateOf(false) }
    var latestVideoPath by remember { mutableStateOf<String?>(null) }

    // 实时检测无障碍服务状态
    val isAccessibilityEnabled by remember {
        mutableStateOf(ControlAccessibilityService.isServiceRunning())
    }

    if (showCameraScreen) {
        CameraScreen(
            onBackClick = { showCameraScreen = false },
            onRecordingComplete = { videoPath ->
                hasVideo = true
                latestVideoPath = videoPath
                showCameraScreen = false
            }
        )
    } else {
        MainScreen(
            onRecordClick = { showCameraScreen = true },
            onControlClick = {
                // 跳转到目标 App (抖音/快手)
                val packageManager = context.packageManager
                val intent = packageManager.getLaunchIntentForPackage("com.ss.android.ugc.aweme")
                    ?: packageManager.getLaunchIntentForPackage("com.kuaishou.nebula")
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    Log.w("AppContent", "Douyin/Kuaishou not installed")
                }
            },
            onAnalyzeClick = {
                if (latestVideoPath != null) {
                    isAnalyzing = true
                    analysisProgress = "初始化分析引擎..."

                    CoroutineScope(Dispatchers.Main).launch {
                        val result = analyzeVideo(
                            context = context,
                            videoPath = latestVideoPath!!
                        ) { current, total, phase ->
                            analysisProgress = "$phase: $current/$total"
                        }

                        actionSequence = result
                        isAnalyzing = false
                        analysisProgress = ""
                    }
                }
            },
            onExecuteClick = {
                // 发送动作序列给无障碍服务执行
                if (actionSequence.isNotEmpty()) {
                    val intent = android.content.Intent(context, ControlAccessibilityService::class.java).apply {
                        putStringArrayListExtra(
                            ControlAccessibilityService.EXTRA_ACTION_SEQUENCE,
                            ArrayList(actionSequence)
                        )
                    }
                    context.startService(intent)
                    Log.i("AppContent", "Sent ${actionSequence.size} actions to service")
                }
            },
            onSettingsClick = onOpenSettings,
            actionSequence = actionSequence,
            isAnalyzing = isAnalyzing,
            analysisProgress = analysisProgress,
            hasVideo = hasVideo,
            isAccessibilityEnabled = isAccessibilityEnabled
        )
    }
}

/**
 * 分析视频文件，返回动作序列
 *
 * 流程:
 * 1. 提取视频帧
 * 2. 姿态估计 (MediaPipe Pose Landmarker)
 * 3. 动作分类 (TFLite)
 */
suspend fun analyzeVideo(
    context: android.content.Context,
    videoPath: String,
    onProgress: (current: Int, total: Int, phase: String) -> Unit
): List<String> = withContext(Dispatchers.Default) {
    val frameExtractor = FrameExtractor(context)
    val poseEstimator = PoseEstimator(context)
    val actionClassifier = ActionClassifier(context)
    var frames: List<android.graphics.Bitmap> = emptyList()

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
