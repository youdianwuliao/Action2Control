# Action2Control 需求文档

## 项目概述
开发一个安卓 App，通过身体动作识别控制其他 App（抖音/快手等）。

## 技术栈
- 语言: Kotlin
- UI: Jetpack Compose
- 姿态识别: MediaPipe Pose Landmarker
- 动作分类: TensorFlow Lite
- 手势控制: AccessibilityService
- 最低 SDK: API 24 (Android 7.0)
- 目标 SDK: API 34 (Android 14)

## 功能需求

### R1: 权限管理
- R1.1 启动时请求相机权限 (CAMERA)
- R1.2 启动时请求录音权限 (RECORD_AUDIO)
- R1.3 提供无障碍服务开启引导

### R2: 相机录制
- R2.1 CameraX 全屏相机预览
- R2.2 圆形录制按钮，支持开始/停止
- R2.3 视频保存到应用私有目录

### R3: 姿态估计
- R3.1 从视频逐帧提取 Bitmap
- R3.2 MediaPipe Pose Landmarker 识别 33 个关键点
- R3.3 返回每帧关键点坐标

### R4: 动作分类
- R4.1 加载 TFLite 模型 (输入 [1,99], 输出 [1,5])
- R4.2 classify 方法返回概率最高动作
- R4.3 模型缺失时返回 "unknown"
- R4.4 提供 Python 训练脚本

### R5: 动作序列处理
- R5.1 合并连续相同动作
- R5.2 过滤 <5 帧的噪声
- R5.3 输出去抖后动作序列

### R6: 无障碍服务
- R6.1 ControlAccessibilityService
- R6.2 动作映射: swipe_up, swipe_down, like, back
- R6.3 dispatchGesture 执行手势
- R6.4 AndroidManifest 声明服务

### R7: 主流程
- R7.1 底部按钮: "录制动作" / "开始控制"
- R7.2 录制完成返回主界面
- R7.3 "分析动作" 显示动作序列
- R7.4 "执行动作" 发送序列给服务
