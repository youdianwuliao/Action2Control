package com.action2control.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.action2control.camera.ScreenRecorder

private const val TAG = "CameraScreen"

/**
 * 屏幕录制界面
 * 显示录制状态 + 控制按钮（开始/暂停/恢复/停止）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBackClick: () -> Unit,
    onRecordingComplete: (videoPath: String) -> Unit
) {
    val context = LocalContext.current

    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var screenRecorder by remember { mutableStateOf<ScreenRecorder?>(null) }
    var mediaProjectionData by remember { mutableStateOf<Pair<Int, Intent>?>(null) }

    // 录音权限请求
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (!granted) {
                errorMessage = "需要录音权限才能录制视频"
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    )

    // MediaProjection 授权请求
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                mediaProjectionData = result.resultCode to result.data!!
                // 授权成功，可以开始录制
                Toast.makeText(context, "屏幕录制授权成功，点击开始录制", Toast.LENGTH_SHORT).show()
            } else {
                errorMessage = "屏幕录制授权被拒绝"
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    )

    // 初始化 ScreenRecorder
    LaunchedEffect(Unit) {
        screenRecorder = ScreenRecorder(
            context = context,
            onRecordingComplete = { videoPath ->
                isRecording = false
                isPaused = false
                onRecordingComplete(videoPath)
            },
            onRecordingError = { error ->
                isRecording = false
                isPaused = false
                errorMessage = error
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        )

        // 请求录音权限
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // 清理资源
    DisposableEffect(screenRecorder) {
        onDispose {
            screenRecorder?.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录制屏幕操作") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 录制状态显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 状态指示器
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isRecording && !isPaused -> Color.Red
                                    isRecording && isPaused -> Color.Yellow
                                    else -> Color.Gray
                                }
                            )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            !isRecording && mediaProjectionData == null -> "等待屏幕录制授权..."
                            !isRecording -> "准备就绪，点击开始录制"
                            isRecording && !isPaused -> "正在录制中..."
                            isRecording && isPaused -> "已暂停"
                            else -> "未知状态"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // 使用说明
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "使用说明",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 点击「开始」授权屏幕录制\n" +
                                "2. 切换到目标 App 进行操作\n" +
                                "3. 点击「暂停」可暂停录制\n" +
                                "4. 完成后点击「停止」保存视频",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 控制按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRecording) {
                    // 开始按钮
                    if (mediaProjectionData == null) {
                        Button(
                            onClick = {
                                val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                            },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "授权屏幕录制",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val recorder = screenRecorder ?: return@Button
                                val (resultCode, data) = mediaProjectionData!!
                                if (recorder.startRecording(resultCode, data)) {
                                    isRecording = true
                                    isPaused = false
                                }
                            },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "开始录制",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                } else {
                    // 暂停/恢复按钮
                    Button(
                        onClick = {
                            val recorder = screenRecorder ?: return@Button
                            if (isPaused) {
                                recorder.resumeRecording()
                                isPaused = false
                            } else {
                                recorder.pauseRecording()
                                isPaused = true
                            }
                        },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "恢复录制" else "暂停录制",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 停止按钮
                    Button(
                        onClick = {
                            screenRecorder?.stopRecording()
                        },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "停止录制",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // 错误提示
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
