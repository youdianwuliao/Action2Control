# Action2Control 项目索引

## 项目概述
Action2Control 是一个 Android 应用，通过身体动作识别来控制其他 App（如抖音/快手）。用户录制视频，App 自动识别其中的身体动作并映射为对手机其他 App 的控制指令（上滑、点赞、返回等）。

## 技术栈
- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **最低 SDK**: API 24 (Android 7.0)
- **目标 SDK**: API 34 (Android 14)
- **姿态识别**: MediaPipe Pose Landmarker
- **动作分类**: TensorFlow Lite
- **手势控制**: AccessibilityService
- **相机**: CameraX

## 核心模块

| 模块 | 路径 | 说明 |
|------|------|------|
| 主界面 | `ui/MainScreen.kt` | 主界面 UI，包含录制、分析、执行按钮 |
| 相机界面 | `ui/CameraScreen.kt` | 相机预览和录制界面 |
| 视频录制 | `camera/VideoRecorder.kt` | CameraX 视频录制核心逻辑 |
| 权限管理 | `utils/PermissionHelper.kt` | 权限请求和无障碍服务引导 |
| 主 Activity | `MainActivity.kt` | 应用入口和状态管理 |
| 姿态估计 | `ml/PoseEstimator.kt` | MediaPipe 姿态识别 |
| 动作分类 | `ml/ActionClassifier.kt` | TFLite 动作分类 |
| 帧提取 | `ml/FrameExtractor.kt` | 视频帧提取 |
| 序列提取 | `ml/ActionSequenceExtractor.kt` | 动作去抖和序列合并 |
| 无障碍服务 | `service/ControlAccessibilityService.kt` | 手势执行服务 |
| 动作分发 | `service/ActionDispatcher.kt` | 动作到手势的映射 |

## 配置文件

| 文件 | 说明 |
|------|------|
| `build.gradle.kts` | 根构建配置 |
| `app/build.gradle.kts` | 模块构建配置和依赖 |
| `AndroidManifest.xml` | 应用清单和权限声明 |
| `res/xml/accessibility_service_config.xml` | 无障碍服务配置 |

## 文档链接

- [架构文档](./ARCHITECTURE.md)
- [接口文档](./INTERFACES.md)
- [开发者指南](./DEVELOPER_GUIDE.md)
- [需求文档](../specs/action2control/requirements.md)
- [任务列表](../specs/action2control/tasklist.md)
