package com.action2control.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 主界面
 * 底部两个按钮: "录制动作" 和 "开始控制"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRecordClick: () -> Unit,
    onControlClick: () -> Unit,
    onAnalyzeClick: () -> Unit,
    onExecuteClick: () -> Unit,
    onSettingsClick: () -> Unit,
    actionSequence: List<String> = emptyList(),
    isAnalyzing: Boolean = false,
    analysisProgress: String = "",
    hasVideo: Boolean = false,
    isAccessibilityEnabled: Boolean = false
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Action2Control", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 状态提示区域
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!hasVideo) {
                    Text(
                        text = "尚未录制视频",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (!isAccessibilityEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "无障碍服务未开启",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 动作序列显示区域
            if (actionSequence.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "动作序列",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(actionSequence) { action ->
                                Text(
                                    text = "• $action",
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 操作按钮区域
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 录制按钮
                Button(
                    onClick = onRecordClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true
                ) {
                    Text("录制动作", fontSize = 16.sp)
                }

                // 分析按钮 (录制完成后启用)
                Button(
                    onClick = onAnalyzeClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasVideo && !isAnalyzing
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("分析中...", fontSize = 16.sp)
                            if (analysisProgress.isNotEmpty()) {
                                Text(analysisProgress, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Text("分析动作", fontSize = 16.sp)
                    }
                }

                // 执行按钮 (无障碍服务启用后启用)
                Button(
                    onClick = onExecuteClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAccessibilityEnabled && actionSequence.isNotEmpty()
                ) {
                    Text("执行动作", fontSize = 16.sp)
                }

                // 开始控制按钮 (直接跳转到目标 App)
                OutlinedButton(
                    onClick = onControlClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始控制", fontSize = 16.sp)
                }
            }
        }
    }
}
