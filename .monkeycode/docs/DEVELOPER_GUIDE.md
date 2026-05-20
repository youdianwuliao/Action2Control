# Action2Control 开发者指南

## 环境要求

- **Android Studio**: Hedgehog 或更新版本
- **JDK**: 17
- **Gradle**: 8.2
- **最低 SDK**: API 24 (Android 7.0)
- **目标 SDK**: API 34 (Android 14)

## 项目构建

```bash
# 同步 Gradle 依赖
./gradlew build

# 清理构建
./gradlew clean

# 运行测试
./gradlew test
```

## 开发工作流

### 1. 权限管理
应用启动时自动请求以下权限:
- `CAMERA` - 相机访问
- `RECORD_AUDIO` - 音频录制

权限请求在 `MainActivity.onCreate()` 中触发，通过 `PermissionHelper` 管理。

### 2. 相机录制流程

```
MainActivity → CameraScreen → VideoRecorder
                  ↓
              onRecordingComplete(videoPath)
                  ↓
          hasVideo = true, latestVideoPath = videoPath
                  ↓
              返回 MainScreen
```

### 3. 视频存储
- **路径**: `context.getExternalFilesDir(null)/video_{timestamp}.mp4`
- **格式**: MP4
- **质量**: HD (1280x720)，降级到 SD

## 代码规范

### Kotlin 约定
- 使用 `suspend` 函数处理异步操作
- 使用 `mutableStateOf` 管理 Compose 状态
- 资源释放使用 `DisposableEffect`

### 命名规范
- 类名: PascalCase (如 `VideoRecorder`)
- 函数名: camelCase (如 `startRecording`)
- 常量: UPPER_SNAKE_CASE (如 `TAG`)
- 私有属性: camelCase (如 `isRecording`)

## 调试技巧

### 日志标签
- `VideoRecorder` - 视频录制相关日志
- `CameraScreen` - 相机界面相关日志

### 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 相机初始化失败 | 权限未授予 | 检查权限请求流程 |
| 录制无声音 | RECORD_AUDIO 权限未授予 | 确保请求录音权限 |
| 视频文件为空 | 录制时间太短 | 确保录制至少 1 秒 |

## 模块开发顺序

| 阶段 | 任务 | 状态 |
|------|------|------|
| 1 | 项目骨架 + 权限管理 + 主界面 | ✅ 完成 |
| 2 | 相机录制模块 | ✅ 完成 |
| 3 | 姿态估计 + 动作分类 | ✅ 完成 |
| 4 | 动作序列处理 | ✅ 完成 |
| 5 | 无障碍服务 | ✅ 完成 |
| 6 | 主流程串联 | ✅ 完成 |
| 7 | 文档与测试 | 未开始 |

## 测试设备要求

- **Android 版本**: 10+ (推荐真机)
- **相机**: 后置摄像头
- **存储**: 至少 100MB 可用空间

## 注意事项

1. **模拟器限制**: 模拟器相机可能有问题，建议使用真机测试
2. **无障碍服务**: 需要用户手动在系统设置中开启
   - 设置 → 无障碍 → 已下载的应用 → Action2Control → 开启
   - 或通过 App 右上角设置按钮直接跳转
3. **MediaPipe 模型**: 首次使用会下载模型文件，需要网络连接
4. **TFLite 模型**: 当前使用占位模型，实际动作识别需要训练自己的模型

## 无障碍服务使用指南

### 开启步骤
1. 首次打开 App 时，点击"设置"按钮跳转到无障碍设置页
2. 找到"Action2Control"并开启服务
3. 返回 App，"执行动作"按钮将变为可用状态

### 执行流程
```
用户点击"执行动作"
    ↓
Intent 发送动作序列 (ArrayList<String>)
    ↓
ControlAccessibilityService.onStartCommand() 接收
    ↓
ActionDispatcher 依次执行每个动作 (间隔 500ms)
    ↓
dispatchGesture 执行手势 (swipe/tap)
    ↓
目标 App (抖音/快手) 响应手势
```

### 动作映射表
| 动作名称 | 手势效果 | 抖音对应操作 |
|---------|---------|------------|
| `swipe_up` | 从下 2/3 滑向上 1/3 | 刷下一个视频 |
| `swipe_down` | 从上 1/3 滑向下 2/3 | 刷上一个视频 |
| `like` | 屏幕中央点击 | 点赞 |
| `back` | 全局返回 | 返回上一页 |
