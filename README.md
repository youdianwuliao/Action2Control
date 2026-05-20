📱 项目名称：Action2Control —— 通过动作控制手机 App
一、项目概述
目标：开发一个安卓 App，允许用户录制一段视频（或实时预览），自动识别其中的身体动作（如举手、下蹲、滑动手势等），并将这些动作映射为对手机其他 App（如抖音、快手）的控制指令（上滑、点赞、返回等）。

核心理念：利用 MediaPipe 进行本地姿态估计，用 TensorFlow Lite 做轻量级动作分类，最后通过 AccessibilityService 实现对其他 App 的控制。

技术栈：Kotlin + Jetpack Compose + MediaPipe + TensorFlow Lite + AccessibilityService。

二、环境与工具准备（请 AI 忽略环境安装，直接生成代码，但要在注释中说明依赖）
Android Studio Hedgehog 或更新版本

最低 SDK：API 24（Android 7.0）

目标 SDK：API 34（Android 14）

关键依赖：

com.google.mediapipe:tasks-vision:latest.release

org.tensorflow:tensorflow-lite:2.16.1

org.tensorflow:tensorflow-lite-support:0.4.4

androidx.compose.ui:ui:1.7.0（等 Compose 全家桶）

三、功能模块与开发顺序（AI 请按此顺序生成代码）
模块 1：基础 UI 与权限申请
功能：启动页 → 请求必要权限（相机、屏幕录制、无障碍服务）。
关键类：MainActivity.kt、PermissionHelper.kt。
AI 提示词片段：

“请用 Kotlin + Jetpack Compose 编写一个主界面，底部有两个按钮：‘录制动作’和‘开始控制’。首先检查并请求 CAMERA 和 RECORD_AUDIO 权限（如果需要屏幕录制则使用 MediaProjection 的权限）。无障碍服务权限提供一个引导用户去设置页面开启的 Intent。”

模块 2：屏幕录制（MediaProjection）
功能：录制当前屏幕内容（或者前置/后置摄像头？——决定：用后置摄像头更方便做身体动作识别，所以不用屏幕录制，而是直接用 CameraX 获取视频流）。
更正：为了“录制一段视频”，最简单的是打开相机预览并录制，而不是录制手机屏幕。这样用户对着摄像头做动作，App 记录并分析。
所以：模块 2 = 相机预览 + 视频录制。
关键类：CameraScreen.kt（使用 CameraX）、VideoRecorder.kt。
AI 提示词：

“使用 CameraX 实现一个全屏预览界面，可以开始/停止录制视频，录制完成后将视频文件保存到应用私有目录。用 Compose 布局，包含一个圆形录制按钮。”

模块 3：视频帧抽取与姿态估计（MediaPipe）
功能：从录制的视频文件中逐帧读取，对每一帧调用 MediaPipe Pose Landmarker 获取 33 个人体关键点的坐标。
关键类：PoseEstimator.kt、FrameExtractor.kt（使用 MediaMetadataRetriever）。
AI 提示词：

“编写一个 PoseEstimator 类，输入一个视频文件路径，逐帧提取 Bitmap，并使用 MediaPipe Pose Landmarker 返回每帧的人体关键点列表（List<NormalizedLandmark>）。需要初始化 PoseLandmarker 选项，使用 POSE_LANDMARKER_TASK 模型，并配置为在 CPU 上运行。”

模块 4：动作分类（TFLite 分类器）
功能：将每一帧的姿态关键点（33×3 坐标）输入到一个小型 TensorFlow Lite 分类模型，输出动作标签（如 “swipe_up”, “like”, “wave_hand”）。
注意：模型需要预训练。我们将提供一个假的占位模型用于演示，并提供一个训练脚本（Python）供用户自行扩展。
关键类：ActionClassifier.kt。
AI 提示词：

“创建一个 ActionClassifier 类，加载 assets 目录下的‘action_model.tflite’模型（输入形状为 [1, 99] 即33关键点×3坐标，输出为 [1, 5] 对应5种动作的概率）。提供一个 classify(landmarks: List<NormalizedLandmark>): String 方法，返回概率最高的动作名称。模型不存在时返回‘unknown’，并给出 log 警告。”

同时，生成一个简单的 Python 脚本（train_model.py）用来训练一个虚拟的 5 类动作分类器，并转换成 tflite 格式，放在 assets 里。

模块 5：动作序列提取与去抖动
功能：从一整段视频的动作分类结果中，合并连续的相同动作，过滤太短暂的噪声（例如只持续 <5 帧的动作忽略），输出一个动作序列。
关键类：ActionSequenceExtractor.kt。
AI 提示词：

“实现一个函数 extractSequence(actionList: List<String>, minFrames: Int = 5): List<String>，将连续相同动作合并，并丢弃持续时间不足 minFrames 的孤立即时动作。返回去抖后的动作列表。”

模块 6：无障碍服务控制其他 App
功能：根据提取的动作序列，在用户点击“开始控制”后，依序执行对应的 UI 操作（例如上滑、点击屏幕中央）。
关键类：ControlAccessibilityService.kt（继承 AccessibilityService）、ActionDispatcher.kt。
AI 提示词：

“编写一个无障碍服务 ControlAccessibilityService，在 onStartCommand 中接收一个动作名称字符串，然后执行：

‘swipe_up’: 模拟从 (width/2, height*2/3) 到 (width/2, height/3) 的滑动

‘swipe_down’: 相反方向

‘like’: 在屏幕中央执行一个点击

‘back’: 执行全局返回
需要动态获取屏幕尺寸，并使用 dispatchGesture 方法。在 AndroidManifest 中声明服务，并提示用户开启无障碍权限。”

模块 7：主流程串联
功能：将以上模块串联为用户可操作的完整流程：

录制视频并保存；

点击“分析动作”按钮，调用 PoseEstimator 和 ActionClassifier 处理视频；

显示提取的动作序列；

用户点击“回放动作控制抖音/快手”，无障碍服务依次执行动作。

AI 提示词：

“在 MainActivity 中添加以下逻辑：

录制按钮启动 CameraScreen，录制完成后返回 MainActivity。

分析按钮读取最新视频，调用 PoseEstimator 和 ActionClassifier，最后将去抖后的动作序列显示在 Text 中。

执行按钮启动 ControlAccessibilityService（如果未开启则引导开启），并将动作序列通过 Intent 发送给服务依次执行。”

四、额外需要的文件（AI 需一并生成）
AndroidManifest.xml：声明相机、录制音频权限，无障碍服务，MediaProjection 权限（可选）。

build.gradle (Module)：完整依赖列表。

assets/action_model.tflite：占位模型文件（如果没有真实模型，可以生成一个随机权重模型）。

train_model.py：用于用户自己训练真实动作模型的脚本（使用 MediaPipe Holistic 提取关键点数据训练）。

README.md：如何构建、开启无障碍权限、如何替换自己的模型的说明。

五、给 AI 的最终系统级提示（复制到对话中）
你是一个资深安卓架构师。请按照以下详细计划生成一个完整的 Android 项目源代码，用于实现“通过身体动作控制手机 App”。项目名：Action2Control。
要求：

使用 Kotlin + Jetpack Compose。

所有类文件单独列出，包括包名、import、注释。

生成的代码可以直接复制到 Android Studio 中编译运行（虽然模型是占位的，但框架完整）。

重点处理权限请求、无障碍服务引导、MediaPipe 初始化失败的回退。

提供伪造的 TFLite 模型生成脚本（Python），确保 App 不会因为缺少模型而崩溃。

输出格式：按文件分类，每个文件代码块开头注明路径（如 app/src/main/java/com/action2control/MainActivity.kt）。
请直接生成代码，不要省略关键部分。

六、测试与验证步骤（用户拿到代码后操作）
在 Android Studio 中打开项目，Sync Gradle 下载依赖。

连接一台 Android 10+ 的真机（模拟器相机可能有问题）。

运行 App → 授予相机权限。

录制一段自己做的简单动作（例如先举手 2 秒，再向下挥手）。

点击“分析动作” → 因为模型是随机的，可能输出随机标签，但能看到流程跑通。

去系统设置中开启“Action2Control”的无障碍服务。

回到 App，点击“执行动作” → 观察手机是否执行了滑动/点击动作。

七、已知局限与后续优化方向（告知 AI 可以在注释中提及）
真实动作识别需要训练自己的 TFLite 模型（提供 Python 脚本）。

性能优化：可以使用 MediaPipe 的实时流处理代替逐帧分析。

无障碍手势需要 Android 7.0+，且部分 ROM 可能需要额外配置。

控制抖音/快手时，需要提前打开目标 App，因为无障碍服务只是模拟手势，不负责启动 App。

这份计划可以直接作为一个 Prompt 复制给 Claude 3.7 / GPT-4o / DeepSeek 等支持长上下文的 AI。它会按照上述模块生成完整代码。如果你希望我针对某个模块（比如 CameraX 录制 + MediaPipe 实时分析）给出更紧凑的示例，也可以告诉我，我可以帮你先写一个核心片段。
