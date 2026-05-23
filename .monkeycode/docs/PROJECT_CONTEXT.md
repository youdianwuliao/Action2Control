# Action2Control - 项目上下文文档

> 本文档供 AI Agent 快速了解项目背景、架构和关键实现细节。

## 1. 项目概览

**Action2Control** 是一个 Android 自动化工具，能够**录制用户的屏幕操作**（点击、滑动），将其转化为**动作序列**，并**自动回放**这些操作。

- **核心功能**:
  1. **录制**: 屏幕录制 + 视觉分析 (MediaPipe + TFLite)。
  2. **分析**: 姿态识别 -> 动作分类 -> 序列去重。
  3. **回放**: 基于 AccessibilityService 的无障碍手势执行。
- **当前版本**: v0.26
- **包名**: `com.action2control`

## 2. 技术栈

- **语言**: Kotlin + Java (部分 API 34+ 反射辅助)
- **UI**: Jetpack Compose (Material 3)
- **关键 API**:
  - `MediaProjection` / `VirtualDisplay`: 屏幕捕获
  - `MediaRecorder`: H.264 视频编码
  - `AccessibilityService`: 全局手势模拟 (`dispatchGesture`)
  - `Service`: 前台悬浮窗服务 (`FloatingControlService`)
- **AI/ML**:
  - `MediaPipe Tasks Vision`: 人体姿态估计 (Pose Landmarker)
  - `TensorFlow Lite`: 动作分类 (`action_model.tflite`)
    - 输入: `[1, 99]` (33 个关键点 x/y/z)
    - 输出: `5` 类概率 (`swipe_up`, `swipe_down`, `like`, `back`, `unknown`)

## 3. 核心架构

### 模块结构
```
com.action2control/
├── MainActivity.kt            # 入口，动作库列表，前置条件检查
├── VideoAnalyzer.kt           # 分析管道编排 (帧提取 -> 姿态 -> 分类 -> 序列)
├── service/
│   ├── FloatingControlService.kt  # 悬浮窗录制/执行控制，前台服务
│   ├── ControlAccessibilityService.kt # 无障碍手势执行服务
│   └── ActionDispatcher.kt    # 手势动作分发 (Swipe/Tap/Back)
├── camera/
│   ├── VideoRecorder.kt       # MediaProjection + MediaRecorder 封装
│   └── MediaProjectionHelper.java # API 34+ createVirtualDisplay 反射辅助
├── ml/
│   ├── PoseEstimator.kt       # MediaPipe Pose 封装
│   ├── ActionClassifier.kt    # TFLite 模型推理
│   ├── FrameExtractor.kt      # 视频帧提取 (200ms 间隔, RGB_565)
│   └── ActionSequenceExtractor.kt # 动作去重/去抖逻辑
├── data/
│   └── ActionRepository.kt    # SharedPreferences 持久化动作列表
└── utils/
    └── PermissionHelper.kt    # 权限辅助
```

### 关键数据流

**录制流程**:
`Start` -> `MediaProjection Auth` -> `FloatingControlService` -> `VideoRecorder` (VirtualDisplay) -> `User Action` -> `Stop` -> `analyzeVideo()` -> `Save to Repository`

**分析流程 (analyzeVideo)**:
`Video` -> `FrameExtractor` (Bitmaps) -> `PoseEstimator` (Landmarks) -> `ActionClassifier` (Labels) -> `SequenceExtractor` (Deduplicate) -> `SavedAction`

**执行流程**:
`Select Action` -> `FloatingControlService` -> `ControlAccessibilityService` -> `ActionDispatcher` (DispatchGestureAsync) -> `Loop`

## 4. 关键实现细节与避坑指南

### MediaRecorder 稳定性
- **Stop Crash**: `MediaRecorder.stop()` 在录制极短时（<1s）常抛异常。
  - **解决方案**: 捕获异常，若文件大小 > 20KB 则视为有效，继续分析。
- **API 34 VirtualDisplay**: 签名变更导致 Kotlin 解析错误。
  - **解决方案**: `MediaProjectionHelper.java` 使用 `Method.invoke` 反射调用正确重载。
- **尺寸限制**: MediaRecorder 要求宽高为偶数，代码中已自动对齐。

### 性能与内存
- **Bitmap 处理**: 提取帧后转换为 `RGB_565`，内存减半，防止 OOM。
- **TFLite Buffer**: 预分配 `ByteBuffer` 复用，减少 GC 压力。
- **Bitmap Release**: 必须检查 `isRecycled` 再释放，防止双重释放崩溃。

### 服务生命周期
- `FloatingControlService` 使用 `serviceScope` (`SupervisorJob`) 管理协程，`onDestroy` 中取消。
- 停止服务必须使用 `stopSelf(startId)`，防止 `postDelayed` 误杀新启动的服务。

### x86/x86_64 兼容性
- **MediaPipe** 不支持模拟器架构。
- **解决方案**: `PoseEstimator` 捕获 `UnsatisfiedLinkError` 优雅降级，UI 拦截显示提示。

## 5. 构建与发布

### 构建配置 (`build.gradle.kts`)
- **Splits**: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`, `Universal`。
- **签名**: 当前使用 Debug Keystore 签名 Release 版本。
- **版本**: `versionCode = 26`, `versionName = "0.26"`

### 发布流程
1. `./gradlew assembleRelease`
2. 使用 `jarsigner` 签名 `app-*-release-unsigned.apk`。
3. `gh release create` 上传各架构 APK。

## 6. 常见问题排查 (Troubleshooting)

| 现象 | 可能原因 | 排查方向 |
| :--- | :--- | :--- |
| **未识别到动作** | 1. 录制时间太短/文件过小<br>2. 画面不清晰<br>3. 姿态引擎不可用 | 检查 Logcat: `FrameExtractor` 提取帧数；确认 `PoseEstimator` 初始化成功。 |
| **录制后 App 闪退** | 1. `stop()` 未捕获异常<br>2. `x86` 架构原生库缺失 | 检查 `ScreenRecorder` 异常处理；检查 `UnsatisfiedLinkError`。 |
| **动作无法执行** | 1. 无障碍服务未开启<br>2. `ActionDispatcher` 未就绪 | 检查 `ControlAccessibilityService.isServiceRunning()`。 |
| **API 34 启动崩溃** | `createVirtualDisplay` 方法解析失败 | 检查 `MediaProjectionHelper` 反射逻辑是否生效。 |

## 7. AI 协作规范

- **代码质量**: 遵循六大原则 (功能、设计、可读性、测试、规范、性能/安全)。
- **规则**:
  - 禁止使用 Emoji。
  - 简体中文输出。
  - 不执行 `rm` 等删除命令。
  - 敏感信息 (API Key) 不得泄露。
  - 遇到 `go mod` 401/403 错误时使用 `git credential helper` 修复。
  - 涉及 Web 项目时使用 `/deploy-website` skill。
  - 新功能开发前使用 `/feature-design`。
