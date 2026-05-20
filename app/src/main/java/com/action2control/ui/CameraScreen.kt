package com.action2control.ui

import android.Manifest
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.action2control.camera.VideoRecorder

private const val TAG = "CameraScreen"

/**
 * 相机录制界面
 * 全屏相机预览 + 圆形录制按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBackClick: () -> Unit,
    onRecordingComplete: (videoPath: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isRecording by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoRecorder by remember { mutableStateOf<VideoRecorder?>(null) }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] == true
            val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

            if (cameraGranted && audioGranted) {
                // 权限已授予，初始化在 AndroidView onPreviewStreamStateListener 中处理
            } else {
                errorMessage = "需要相机和录音权限才能录制视频"
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    )

    // 请求权限
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    // 清理资源
    DisposableEffect(videoRecorder) {
        onDispose {
            videoRecorder?.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录制动作") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 相机预览区域
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        post {
                            if (videoRecorder == null && isInitialized.not()) {
                                videoRecorder = createVideoRecorder(
                                    context = ctx,
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = this,
                                    onRecordingComplete = { videoPath ->
                                        isRecording = false
                                        onRecordingComplete(videoPath)
                                    },
                                    onRecordingError = { error ->
                                        isRecording = false
                                        errorMessage = error
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 错误提示
            errorMessage?.let { error ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // 录制按钮
            FloatingActionButton(
                onClick = {
                    val recorder = videoRecorder ?: return@FloatingActionButton
                    if (isRecording) {
                        recorder.stopRecording()
                    } else {
                        recorder.startRecording()
                    }
                },
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .size(72.dp),
                containerColor = if (isRecording)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = if (isRecording) "■" else "●",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    // 初始化相机
    val recorder = videoRecorder
    LaunchedEffect(recorder) {
        if (recorder != null && !isInitialized) {
            val success = recorder.initialize()
            isInitialized = success
        }
    }
}

/**
 * 创建 VideoRecorder 实例
 */
private fun createVideoRecorder(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onRecordingComplete: (videoPath: String) -> Unit,
    onRecordingError: (error: String) -> Unit
): VideoRecorder {
    return VideoRecorder(
        context = context,
        lifecycleOwner = lifecycleOwner,
        previewView = previewView,
        onRecordingComplete = onRecordingComplete,
        onRecordingError = onRecordingError
    )
}
