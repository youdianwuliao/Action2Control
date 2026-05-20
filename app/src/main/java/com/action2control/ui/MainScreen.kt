package com.action2control.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.action2control.data.LoopMode
import com.action2control.data.SavedAction

/**
 * 主界面 - 动作库列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    actions: List<SavedAction>,
    onRecordClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onActionClick: (SavedAction) -> Unit,
    onExecuteClick: (SavedAction) -> Unit,
    onDeleteClick: (SavedAction) -> Unit,
    onAddManualClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Action2Control", fontWeight = FontWeight.Bold)
                        Text("自动刷视频赚金币", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onRecordClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Videocam, contentDescription = "录制")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (actions.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "还没有动作序列",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击右下角录制按钮开始",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(onClick = onAddManualClick) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("手动创建")
                        }
                    }
                }
            } else {
                // 动作库列表
                Text(
                    "我的动作库 (${actions.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(actions) { action ->
                        ActionCard(
                            action = action,
                            onClick = { onActionClick(action) },
                            onExecute = { onExecuteClick(action) },
                            onDelete = { onDeleteClick(action) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 动作卡片
 */
@Composable
fun ActionCard(
    action: SavedAction,
    onClick: () -> Unit,
    onExecute: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    action.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    IconButton(onClick = onExecute, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "执行",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 动作列表预览
            Text(
                action.actions.joinToString(" → "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 循环信息
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            when (action.loopMode) {
                                LoopMode.INFINITE -> "无限循环"
                                LoopMode.COUNT -> "${action.loopCount} 次"
                            },
                            fontSize = 11.sp
                        )
                    },
                    enabled = false,
                    modifier = Modifier.height(24.dp)
                )
                AssistChip(
                    onClick = { },
                    label = { Text("${action.actions.size} 个动作", fontSize = 11.sp) },
                    enabled = false,
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

/**
 * 新建动作对话框
 */
@Composable
fun NewActionDialog(
    onSave: (name: String, actions: List<String>, loopMode: LoopMode, loopCount: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var actionText by remember { mutableStateOf("") }
    var loopMode by remember { mutableStateOf(LoopMode.INFINITE) }
    var loopCount by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动创建动作序列") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = actionText,
                    onValueChange = { actionText = it },
                    label = { Text("动作序列 (用逗号分隔，如: swipe_up,like,swipe_up)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("循环模式:")
                    FilterChip(
                        selected = loopMode == LoopMode.INFINITE,
                        onClick = { loopMode = LoopMode.INFINITE },
                        label = { Text("无限") }
                    )
                    FilterChip(
                        selected = loopMode == LoopMode.COUNT,
                        onClick = { loopMode = LoopMode.COUNT },
                        label = { Text("指定次数") }
                    )
                    if (loopMode == LoopMode.COUNT) {
                        OutlinedTextField(
                            value = loopCount,
                            onValueChange = { loopCount = it },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val actionsList = actionText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (name.isNotBlank() && actionsList.isNotEmpty()) {
                        onSave(name, actionsList, loopMode, loopCount.toIntOrNull() ?: 10)
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 动作详情对话框
 */
@Composable
fun ActionDetailDialog(
    action: SavedAction,
    onDismiss: () -> Unit,
    onEdit: (SavedAction) -> Unit
) {
    var editMode by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(action.name) }
    var editLoopMode by remember { mutableStateOf(action.loopMode) }
    var editLoopCount by remember { mutableStateOf(action.loopCount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editMode) "编辑动作" else "动作详情") },
        text = {
            Column {
                if (editMode) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("循环:")
                        FilterChip(
                            selected = editLoopMode == LoopMode.INFINITE,
                            onClick = { editLoopMode = LoopMode.INFINITE },
                            label = { Text("无限") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = editLoopMode == LoopMode.COUNT,
                            onClick = { editLoopMode = LoopMode.COUNT },
                            label = { Text("次数") }
                        )
                        if (editLoopMode == LoopMode.COUNT) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = editLoopCount,
                                onValueChange = { editLoopCount = it },
                                modifier = Modifier.width(80.dp),
                                singleLine = true
                            )
                        }
                    }
                } else {
                    Text("名称: ${action.name}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("循环模式: ${
                        when (action.loopMode) {
                            LoopMode.INFINITE -> "无限循环"
                            LoopMode.COUNT -> "${action.loopCount} 次"
                        }
                    }")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("动作序列:", fontWeight = FontWeight.Bold)
                    LazyColumn {
                        items(action.actions.size) { index ->
                            Text("${index + 1}. ${action.actions[index]}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (editMode) {
                TextButton(
                    onClick = {
                        val updated = action.copy(
                            name = editName,
                            loopMode = editLoopMode,
                            loopCount = editLoopCount.toIntOrNull() ?: 10
                        )
                        onEdit(updated)
                    }
                ) {
                    Text("保存")
                }
            } else {
                TextButton(onClick = { editMode = true }) {
                    Text("编辑")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
