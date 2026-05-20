# 模块文档索引

## ML 模块 (机器学习)

### PoseEstimator
- **路径**: `app/src/main/java/com/action2control/ml/PoseEstimator.kt`
- **功能**: 使用 MediaPipe Pose Landmarker 进行姿态估计
- **输入**: Bitmap 帧列表
- **输出**: `List<List<NormalizedLandmark>>` (每帧 33 个关键点)
- **模型**: `pose_landmarker.task` (从 assets 加载)

### ActionClassifier
- **路径**: `app/src/main/java/com/action2control/ml/ActionClassifier.kt`
- **功能**: 使用 TFLite 模型对姿态关键点进行分类
- **输入**: `List<NormalizedLandmark>` (33 关键点 × 3 坐标)
- **输出**: 动作标签 (swipe_up, swipe_down, like, back, unknown)
- **模型**: `action_model.tflite` (从 assets 加载)

### FrameExtractor
- **路径**: `app/src/main/java/com/action2control/ml/FrameExtractor.kt`
- **功能**: 从视频文件提取 Bitmap 帧
- **输入**: 视频文件路径
- **输出**: `List<Bitmap>`
- **参数**: 帧间隔 (默认 500ms), 最大帧数 (默认 100)

## Camera 模块

### VideoRecorder
- **路径**: `app/src/main/java/com/action2control/camera/VideoRecorder.kt`
- **功能**: CameraX 视频录制
- **存储**: `getExternalFilesDir(null)/video_{timestamp}.mp4`

## UI 模块

### MainScreen
- **路径**: `app/src/main/java/com/action2control/ui/MainScreen.kt`
- **功能**: 主界面，包含录制、分析、执行按钮和动作序列显示

### CameraScreen
- **路径**: `app/src/main/java/com/action2control/ui/CameraScreen.kt`
- **功能**: 相机预览和录制界面

## Service 模块 (待实现)

### ControlAccessibilityService
- **路径**: `app/src/main/java/com/action2control/service/ControlAccessibilityService.kt`
- **状态**: 阶段 5 实现

### ActionDispatcher
- **路径**: `app/src/main/java/com/action2control/service/ActionDispatcher.kt`
- **状态**: 阶段 5 实现

## 工具模块

### PermissionHelper
- **路径**: `app/src/main/java/com/action2control/utils/PermissionHelper.kt`
- **功能**: 权限请求和无障碍服务引导
