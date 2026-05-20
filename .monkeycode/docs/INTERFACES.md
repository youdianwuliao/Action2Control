# Action2Control 接口文档

## Composable 接口

### MainScreen
主界面 UI 组件

```kotlin
@Composable
fun MainScreen(
    onRecordClick: () -> Unit,           // 录制按钮点击
    onControlClick: () -> Unit,          // 开始控制按钮点击
    onAnalyzeClick: () -> Unit,          // 分析动作按钮点击
    onExecuteClick: () -> Unit,          // 执行动作按钮点击
    onSettingsClick: () -> Unit,         // 设置按钮点击
    actionSequence: List<String>,        // 动作序列数据
    isAnalyzing: Boolean,                // 分析中状态
    hasVideo: Boolean,                   // 是否有视频
    isAccessibilityEnabled: Boolean      // 无障碍服务状态
)
```

### CameraScreen
相机录制界面

```kotlin
@Composable
fun CameraScreen(
    onBackClick: () -> Unit,             // 返回按钮点击
    onRecordingComplete: (videoPath: String) -> Unit  // 录制完成回调
)
```

## 类接口

### VideoRecorder
视频录制器

```kotlin
class VideoRecorder(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onRecordingComplete: (videoPath: String) -> Unit,
    onRecordingError: (error: String) -> Unit
) {
    val isRecordingState: Boolean
    
    suspend fun initialize(): Boolean
    fun startRecording()
    fun stopRecording()
    fun release()
}
```

### PermissionHelper
权限管理工具 (object)

```kotlin
object PermissionHelper {
    fun createPermissionLauncher(
        context: Context,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ): ActivityResultLauncher<Array<String>>
    
    fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>)
    
    fun isCameraGranted(context: Context): Boolean
    fun isAudioGranted(context: Context): Boolean
    fun isAccessibilityServiceEnabled(context: Context, serviceName: String): Boolean
    fun getAccessibilitySettingsIntent(context: Context): Intent
    fun getAccessibilityServiceComponentName(context: Context): String
}
```

## 配置文件接口

### accessibility_service_config.xml
无障碍服务配置

```xml
<accessibility-service
    android:description="@string/accessibility_service_required"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:notificationTimeout="100" />
```

## 已实现接口

### PoseEstimator
姿态估计器

```kotlin
class PoseEstimator(context: Context) {
    suspend fun initialize(): Boolean
    suspend fun estimatePoses(
        frames: List<Bitmap>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<List<NormalizedLandmark>>
    fun close()
    fun isReady(): Boolean
}
```

### ActionClassifier
动作分类器

```kotlin
class ActionClassifier(context: Context) {
    fun loadModel(): Boolean
    fun classify(landmarks: List<NormalizedLandmark>): String
    fun classifyAll(allLandmarks: List<List<NormalizedLandmark>>): List<String>
    fun close()
    fun isReady(): Boolean
}
```

### FrameExtractor
帧提取器

```kotlin
class FrameExtractor(context: Context) {
    suspend fun extractFrames(
        videoPath: String,
        frameIntervalUs: Long = 500_000L,
        maxFrames: Int = 100
    ): List<Bitmap>
}
```

### ActionSequenceExtractor
动作序列提取器

```kotlin
object ActionSequenceExtractor {
    data class ActionSegment(val action: String, val frameCount: Int)
    
    fun extractSequence(
        actionList: List<String>,
        minFrames: Int = 5
    ): List<String>
    
    fun getStatistics(actionList: List<String>): String
}
```

## 待实现接口

### ControlAccessibilityService
无障碍服务

```kotlin
class ControlAccessibilityService : AccessibilityService() {
    companion object {
        const val EXTRA_ACTION_SEQUENCE = "action_sequence"
        fun isServiceRunning(): Boolean
        fun getInstance(): ControlAccessibilityService?
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
}
```

### ActionDispatcher
动作分发器

```kotlin
class ActionDispatcher(
    service: ControlAccessibilityService,
    screenWidth: Int,
    screenHeight: Int
) {
    fun dispatchAction(action: String)
    fun executeSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long = 300L)
    fun executeTap(x: Float, y: Float)
}
```
