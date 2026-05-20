# Action2Control 技术设计

## 架构概述

```
┌─────────────────────────────────────────────────┐
│                    MainActivity                    │
│  ┌─────────────┐  ┌────────────────────────────┐ │
│  │ 录制动作按钮 │  │ 开始控制按钮                │ │
│  └──────┬──────┘  └────────────┬───────────────┘ │
│         │                      │                 │
│  ┌──────▼──────┐  ┌────────────▼───────────────┐ │
│  │ CameraScreen │  │ ActionSequenceDisplay      │ │
│  │ (CameraX)    │  │ + Execute Button           │ │
│  └──────┬──────┘  └────────────┬───────────────┘ │
│         │                      │                 │
│  ┌──────▼──────┐  ┌────────────▼───────────────┐ │
│  │ VideoRecorder│  │ ControlAccessibilityService│ │
│  └──────┬──────┘  └────────────────────────────┘ │
│         │                                        │
│  ┌──────▼──────┐                                 │
│  │ PoseEstimator│                                 │
│  └──────┬──────┘                                 │
│         │                                        │
│  ┌──────▼──────┐                                 │
│  │ ActionClassifier│                              │
│  └──────┬──────┘                                 │
│         │                                        │
│  ┌──────▼──────┐                                 │
│  │ ActionSeqExtractor│                            │
│  └─────────────┘                                 │
└─────────────────────────────────────────────────┘
```

## 包结构

```
com.action2control/
├── MainActivity.kt
├── ui/
│   ├── CameraScreen.kt
│   └── MainScreen.kt
├── camera/
│   └── VideoRecorder.kt
├── ml/
│   ├── PoseEstimator.kt
│   ├── ActionClassifier.kt
│   └── ActionSequenceExtractor.kt
├── service/
│   ├── ControlAccessibilityService.kt
│   └── ActionDispatcher.kt
└── utils/
    └── PermissionHelper.kt
```

## 关键类设计

### PermissionHelper
- 请求相机和录音权限
- 检查无障碍服务是否启用
- 提供跳转设置的 Intent

### VideoRecorder
- 使用 CameraX 实现预览
- 使用 VideoCapture 录制视频
- 保存到应用私有目录

### PoseEstimator
- 初始化 MediaPipe PoseLandmarker
- 从视频文件逐帧提取
- 返回 List<NormalizedLandmark>

### ActionClassifier
- 加载 TFLite 模型
- 输入 [1, 99] 特征向量
- 输出 [1, 5] 概率分布
- 返回最高概率动作标签

### ActionSequenceExtractor
- 合并连续相同动作
- 过滤 <5 帧的噪声
- 返回去抖后序列

### ControlAccessibilityService
- 继承 AccessibilityService
- 接收 Intent 传递的动作序列
- 使用 dispatchGesture 执行手势
- 动作映射: swipe_up, swipe_down, like, back

### ActionDispatcher
- 将动作名称转换为 GestureDescription
- 协调手势执行顺序

## 数据流

1. 用户录制视频 → VideoRecorder 保存文件
2. 用户点击分析 → PoseEstimator 逐帧提取关键点
3. ActionClassifier 对每帧分类 → 动作列表
4. ActionSequenceExtractor 去抖 → 动作序列
5. 用户点击执行 → Intent 发送给 AccessibilityService
6. ActionDispatcher 依次执行手势

## 依赖

```gradle
// MediaPipe
implementation("com.google.mediapipe:tasks-vision:0.10.14")

// TensorFlow Lite
implementation("org.tensorflow:tensorflow-lite:2.16.1")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.runtime:runtime")

// CameraX
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-video:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// Activity Compose
implementation("androidx.activity:activity-compose:1.8.2")
```

## 权限声明

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
```

## 无障碍服务配置

```xml
<service
    android:name=".service.ControlAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```
