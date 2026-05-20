# Action2Control - 通过身体动作控制手机 App

## 项目概述

Action2Control 是一个创新的 Android 应用，允许用户通过身体动作（如举手、下蹲、挥手等）来控制其他 App（如抖音、快手）。用户录制一段自己做动作的视频，App 自动识别其中的姿态并映射为对应的控制指令（上滑刷视频、点赞、返回等）。

**核心理念**: 解放双手，用身体动作与手机互动。

## 功能特性

- **屏幕录制**: 使用 MediaProjection API 录制本机屏幕操作
- **暂停/恢复**: 录制过程中可随时暂停和继续
- **姿态识别**: MediaPipe Pose Landmarker 提取 33 个人体关键点
- **动作分类**: TensorFlow Lite 轻量级模型实时分类
- **手势控制**: AccessibilityService 模拟滑动手势和点击
- **完整流程**: 录制 → 分析 → 执行，一键完成

## 支持的动作

| 动作名称 | 手势效果 | 抖音/快手对应操作 |
|---------|---------|-----------------|
| `swipe_up` | 从屏幕底部向上滑 | 刷下一个视频 |
| `swipe_down` | 从屏幕顶部向下滑 | 刷上一个视频 |
| `like` | 屏幕中央点击 | 点赞 |
| `back` | 全局返回 | 返回上一页 |

## 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **最低 SDK**: API 24 (Android 7.0)
- **目标 SDK**: API 34 (Android 14)
- **屏幕录制**: MediaProjection + MediaRecorder
- **姿态识别**: MediaPipe Tasks Vision (0.10.14)
- **动作分类**: TensorFlow Lite (2.16.1)
- **手势控制**: AccessibilityService

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17
- Android SDK 34
- 推荐真机测试（模拟器相机可能有问题）

### 构建项目

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd Action2Control
   ```

2. **打开 Android Studio**
   - File → Open → 选择项目根目录
   - 等待 Gradle 同步完成（首次需要下载依赖）

3. **构建 APK**
   ```bash
   ./gradlew assembleDebug
   ```
   APK 输出路径: `app/build/outputs/apk/debug/app-debug.apk`

4. **安装到设备**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## 使用指南

### 第一步：授予权限

首次打开 App 时，会请求以下权限：
- **录音权限**: 屏幕录制需要录制系统音频

点击"允许"授予权限。

> **注意**: 屏幕录制时系统会弹出提示，告知用户"某某应用将开始截取/录制您的屏幕"，点击"立即开始"即可。

### 第二步：开启无障碍服务

这是控制其他 App 的必要步骤：

1. 点击 App 右上角的 **设置** 按钮
2. 系统跳转到无障碍设置页面
3. 找到 **"Action2Control"** 并点击进入
4. 开启 **"使用 Action2Control"** 开关
5. 在弹出的确认对话框中点击 **"允许"**

> **注意**: 无障碍服务用于模拟手势操作，不会收集任何用户数据。

### 第三步：录制动作

1. 返回 App 主界面
2. 点击 **"录制动作"** 按钮
3. 首次使用会请求屏幕录制授权，点击 **"立即开始"**
4. 授权完成后，点击 **"开始录制"** 按钮（绿色播放图标）
5. 切换到目标 App（如抖音）进行操作
6. 需要暂停时点击 **"暂停"** 按钮，点击 **"恢复"** 继续录制
7. 完成后点击 **"停止"** 按钮（红色方块图标）保存视频
8. 自动返回主界面

> **提示**: 录制的是本机屏幕操作，不是摄像头画面。录制时可以自由切换到任何 App。

### 第四步：分析动作

1. 确保已录制视频（"尚未录制视频" 提示消失）
2. 点击 **"分析动作"** 按钮
3. 等待分析完成（进度显示在按钮上）
   - 加载分类模型 → 初始化姿态估计 → 提取视频帧 → 姿态估计 → 动作分类
4. 分析完成后，动作序列将显示在屏幕中央的卡片中

### 第五步：执行控制

1. 确保无障碍服务已开启（主界面无 "无障碍服务未开启" 红色提示）
2. 点击 **"开始控制"** 按钮（可选：直接跳转到抖音/快手）
3. 打开目标 App（如抖音）
4. 返回 Action2Control，点击 **"执行动作"** 按钮
5. App 将依次执行识别到的动作序列

## 训练自己的动作模型

当前使用占位模型进行演示。要获得准确的动作识别，需要训练自己的模型。

### 步骤 1：准备数据集

按以下目录结构组织视频数据：

```
data/
├── swipe_up/      # 上滑动作视频（建议 20-50 个）
│   ├── video_001.mp4
│   └── video_002.mp4
├── swipe_down/    # 下滑动作视频
├── like/          # 点赞动作视频
├── back/          # 返回动作视频
└── unknown/       # 未知/过渡动作视频
```

### 步骤 2：安装依赖

```bash
pip install tensorflow mediapipe opencv-python numpy scikit-learn
```

### 步骤 3：运行训练

```bash
python train_model.py --data_dir ./data --output action_model.tflite --epochs 50 --batch_size 16
```

训练完成后，会生成：
- `action_model.tflite` - TFLite 模型文件
- `action_model_labels.txt` - 标签映射文件

### 步骤 4：替换模型

将生成的 `action_model.tflite` 复制到 Android 项目：

```bash
cp action_model.tflite app/src/main/assets/
```

重新构建 App 即可使用训练好的模型。

## 项目结构

```
app/src/main/
├── java/com/action2control/
│   ├── MainActivity.kt                  # 应用入口和状态管理
│   ├── ui/
│   │   ├── MainScreen.kt                # 主界面 Composable
│   │   └── CameraScreen.kt              # 屏幕录制界面 Composable
│   ├── camera/
│   │   └── VideoRecorder.kt             # MediaProjection 屏幕录制
│   ├── ml/
│   │   ├── PoseEstimator.kt             # MediaPipe 姿态估计
│   │   ├── ActionClassifier.kt          # TFLite 动作分类
│   │   ├── FrameExtractor.kt            # 视频帧提取
│   │   └── ActionSequenceExtractor.kt   # 动作序列去抖
│   ├── service/
│   │   ├── ControlAccessibilityService.kt  # 无障碍服务
│   │   └── ActionDispatcher.kt          # 动作到手势映射
│   └── utils/
│       └── PermissionHelper.kt          # 权限管理工具
```

## 已知局限

1. **模型精度**: 占位模型仅用于演示，实际识别需要训练自己的模型
2. **性能**: 视频分析在 CPU 上运行，长视频可能需要较长时间
3. **设备兼容性**: 部分 ROM 可能对无障碍服务有额外限制
4. **实时性**: 当前为录制后分析模式，不支持实时流处理
5. **动作种类**: 仅支持 4 种基本动作，可扩展更多
6. **系统限制**: Android 10+ 录屏时系统会显示通知栏提示，无法隐藏

## 后续优化方向

- [ ] 实时流处理（边录制边识别）
- [ ] 更多动作支持（如双手操作、复杂手势）
- [ ] 模型量化和加速（GPU/NNAPI）
- [ ] 自定义动作录制和训练界面
- [ ] 多目标 App 支持（可配置包名）
- [ ] 动作序列编辑和保存

## 许可证

本项目仅供学习和研究使用。

## 贡献

欢迎提交 Issue 和 Pull Request。

## 联系方式

如有问题或建议，请提交 Issue。
