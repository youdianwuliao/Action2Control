# Action2Control 任务列表

## 阶段 1: 项目骨架 + 权限管理 + 主界面

- [x] 1.1 创建 Android 项目基础结构 (build.gradle, AndroidManifest.xml, 包结构)
- [x] 1.2 实现 PermissionHelper 工具类 (相机/录音权限请求 + 无障碍引导)
- [x] 1.3 实现 MainActivity + MainScreen (底部两个按钮: 录制动作/开始控制)
- [x] 1.4 配置 gradle 依赖 (MediaPipe, TFLite, Compose, CameraX)

## 阶段 2: 相机录制模块

- [x] 2.1 实现 CameraScreen (CameraX 全屏预览 + Compose UI)
- [x] 2.2 实现 VideoRecorder (开始/停止录制 + 保存到私有目录)
- [x] 2.3 集成 CameraScreen 到 MainActivity (录制完成返回)

## 阶段 3: 姿态估计 + 动作分类

- [x] 3.1 实现 PoseEstimator (MediaPipe PoseLandmarker 初始化 + 逐帧提取)
- [x] 3.2 实现 FrameExtractor (从视频文件提取 Bitmap 帧)
- [x] 3.3 实现 ActionClassifier (加载 TFLite 模型 + classify 方法)
- [x] 3.4 创建占位 TFLite 模型文件生成脚本
- [x] 3.5 实现 Python 训练脚本 (train_model.py)

## 阶段 4: 动作序列处理

- [x] 4.1 实现 ActionSequenceExtractor (合并连续动作 + 过滤噪声)
- [x] 4.2 编写单元测试 (去抖逻辑验证)

## 阶段 5: 无障碍服务

- [x] 5.1 创建 accessibility_service_config.xml 配置
- [x] 5.2 实现 ControlAccessibilityService (接收动作 + dispatchGesture)
- [x] 5.3 实现 ActionDispatcher (动作映射 + 手势构建)
- [x] 5.4 AndroidManifest 注册服务

## 阶段 6: 主流程串联

- [x] 6.1 MainActivity 添加分析动作按钮逻辑
- [x] 6.2 显示动作序列结果
- [x] 6.3 执行按钮集成 (Intent 发送动作序列)
- [x] 6.4 错误处理与回退机制

## 阶段 7: 文档与测试

- [x] 7.1 编写 README.md (构建说明 + 权限指南 + 模型替换)
- [x] 7.2 完整流程测试验证
