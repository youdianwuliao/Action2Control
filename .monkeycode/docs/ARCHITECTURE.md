# Action2Control 系统架构

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                        MainActivity                       │
│  ┌─────────────────────────────────────────────────────┐ │
│  │                    AppContent                         │ │
│  │  ┌─────────────┐           ┌──────────────────────┐ │ │
│  │  │ CameraScreen │           │    MainScreen        │ │ │
│  │  │             │           │  ┌──────────────────┐│ │ │
│  │  │ ┌─────────┐ │           │  │ 状态提示区域     ││ │ │
│  │  │ │PreviewView│           │  │ 动作序列显示     ││ │ │
│  │  │ └─────────┘ │           │  │ 操作按钮区域     ││ │ │
│  │  │    FAB      │           │  └──────────────────┘│ │ │
│  │  └──────┬──────┘           └──────────┬───────────┘ │ │
│  │         │                              │             │ │
│  │  ┌──────▼──────┐           ┌───────────▼───────────┐ │ │
│  │  │VideoRecorder│           │  (阶段3-6 待实现)      │ │ │
│  │  │  (CameraX)  │           │  PoseEstimator         │ │ │
│  │  └─────────────┘           │  ActionClassifier      │ │ │
│  │                            │  ActionSeqExtractor    │ │ │
│  │                            │  ControlAccessibility  │ │ │
│  │                            └───────────────────────┘ │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

## 包结构

```
com.action2control/
├── MainActivity.kt              # 应用入口和状态管理
├── ui/
│   ├── MainScreen.kt            # 主界面 Composable
│   └── CameraScreen.kt          # 相机界面 Composable
├── camera/
│   └── VideoRecorder.kt         # 视频录制核心逻辑
├── ml/                          # 机器学习模块 ✅ 已完成
│   ├── PoseEstimator.kt         # MediaPipe 姿态估计
│   ├── ActionClassifier.kt      # TFLite 动作分类
│   ├── FrameExtractor.kt        # 视频帧提取
│   └── ActionSequenceExtractor.kt # 动作序列去抖
├── service/                     # 系统服务模块 ✅ 已完成
│   ├── ControlAccessibilityService.kt # 无障碍服务
│   └── ActionDispatcher.kt      # 动作到手势映射
└── utils/
    └── PermissionHelper.kt      # 权限管理工具
```

## 核心组件

### 1. MainActivity
- **职责**: 应用入口、权限初始化、状态管理
- **关键状态**:
  - `showCameraScreen`: 控制相机界面显示
  - `actionSequence`: 分析后的动作序列
  - `isAnalyzing`: 分析中状态
  - `hasVideo`: 是否有已录制视频
  - `latestVideoPath`: 最新视频文件路径

### 2. VideoRecorder
- **职责**: CameraX 视频录制
- **关键方法**:
  - `initialize()`: 初始化 CameraProvider
  - `startRecording()`: 开始录制
  - `stopRecording()`: 停止录制
  - `release()`: 释放资源
- **存储路径**: `context.getExternalFilesDir(null)/video_{timestamp}.mp4`

### 3. PermissionHelper
- **职责**: 权限请求和无障碍服务引导
- **关键方法**:
  - `createPermissionLauncher()`: 创建权限请求 Launcher
  - `requestPermissions()`: 请求相机和录音权限
  - `isCameraGranted()`: 检查相机权限
  - `isAudioGranted()`: 检查录音权限
  - `isAccessibilityServiceEnabled()`: 检查无障碍服务状态
  - `getAccessibilitySettingsIntent()`: 跳转系统设置

## 数据流

```
用户点击"录制动作"
    ↓
显示 CameraScreen
    ↓
VideoRecorder.initialize() → 绑定 CameraX
    ↓
用户点击录制按钮 → startRecording()
    ↓
视频保存到 getExternalFilesDir(null)
    ↓
用户点击停止 → stopRecording() → onRecordingComplete(videoPath)
    ↓
返回 MainScreen, hasVideo = true, latestVideoPath = videoPath
    ↓
(阶段 3) 用户点击"分析动作" → PoseEstimator → ActionClassifier → ActionSequenceExtractor
    ↓
(阶段 6) 用户点击"执行动作" → Intent → ControlAccessibilityService → dispatchGesture
```

## 依赖关系

### 已集成的依赖
- `androidx.camera:camera-*:1.3.1` - 相机功能
- `androidx.compose:*` - UI 框架
- `com.google.mediapipe:tasks-vision:0.10.14` - 姿态识别 (待使用)
- `org.tensorflow:tensorflow-lite:*` - 动作分类 (待使用)

### 4. ActionSequenceExtractor
- **职责**: 动作序列去抖和合并
- **关键方法**:
  - `extractSequence()`: 合并连续相同动作，过滤噪声段
  - `getStatistics()`: 获取动作统计信息
- **算法**:
  1. 将连续相同动作分组为 ActionSegment
  2. 过滤帧数 < minFrames (默认 5) 的段
  3. 提取剩余段的动作标签

### 5. ControlAccessibilityService
- **职责**: 无障碍服务，执行手势控制其他 App
- **关键方法**:
  - `onStartCommand()`: 接收 Intent 传递的动作序列
  - `executeActionsSequentially()`: 依次执行每个动作 (间隔 500ms)
  - `isServiceRunning()`: 静态方法检测服务状态
- **生命周期**:
  - `onServiceConnected()`: 初始化 ActionDispatcher
  - `onUnbind()`: 清理实例引用

### 6. ActionDispatcher
- **职责**: 将动作名称映射为具体的手势操作
- **动作映射**:
  - `swipe_up`: x=center, y=2/3 → y=1/3 (上滑刷视频)
  - `swipe_down`: x=center, y=1/3 → y=2/3 (下滑)
  - `like`: 屏幕中央点击 (点赞)
  - `back`: performGlobalAction(GLOBAL_ACTION_BACK)
- **手势构建**:
  - `executeSwipe()`: 使用 GestureDescription + StrokeDescription
  - `executeTap()`: 起点终点相同的短路径 (100ms)

## 架构模式

### 状态管理
- 使用 Compose 的 `mutableStateOf` 进行本地状态管理
- `AppContent` 作为状态容器，协调页面导航

### 异步处理
- `suspendCoroutine` 封装 CameraProvider 的 ListenableFuture
- `LaunchedEffect` 处理 Compose 生命周期内的异步初始化

### 资源管理
- `DisposableEffect` 确保相机资源在页面退出时释放
- `VideoRecorder.release()` 解绑所有 CameraX 用例
