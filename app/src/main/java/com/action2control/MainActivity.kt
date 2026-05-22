package com.action2control

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.action2control.data.ActionRepository
import com.action2control.data.LoopMode
import com.action2control.data.SavedAction
import com.action2control.service.ControlAccessibilityService
import com.action2control.service.FloatingControlService
import com.action2control.ui.ActionDetailDialog
import com.action2control.ui.MainScreen
import com.action2control.ui.NewActionDialog

/**
 * 主 Activity
 * 动作库管理 + 录制/执行入口
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var actionRepository: ActionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionRepository = ActionRepository(this)

        setContent {
            MaterialTheme {
                AppContent(
                    actionRepository = actionRepository,
                    onOpenSettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onCheckOverlayPermission = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }
                )
            }
        }
    }
}

@Composable
fun AppContent(
    actionRepository: ActionRepository,
    onOpenSettings: () -> Unit,
    onCheckOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 使用 mutableStateOf 保存动作列表
    var actions by remember { mutableStateOf(actionRepository.getAllActions()) }
    var showNewActionDialog by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf<SavedAction?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<SavedAction?>(null) }

    // 监听 Lifecycle，当 App 回到前台时自动刷新列表
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("AppContent", "Lifecycle ON_RESUME -> Refreshing action list")
                actions = actionRepository.getAllActions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 初始加载一次（如果 ON_RESUME 没有触发）
    LaunchedEffect(Unit) {
        actions = actionRepository.getAllActions()
    }

    // MediaProjection 授权
    var mediaProjectionResult by remember { mutableStateOf<Pair<Int, Intent>?>(null) }
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                mediaProjectionResult = result.resultCode to result.data!!
                // 启动悬浮窗录制
                startRecordFloatingWindow(context, result.resultCode, result.data!!)
            } else {
                Toast.makeText(context, "屏幕录制授权被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun refreshActions() {
        actions = actionRepository.getAllActions()
    }

    fun startRecording() {
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            onCheckOverlayPermission()
            return
        }

        // 请求 MediaProjection 授权
        val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    fun startExecuting(action: SavedAction) {
        // 检查无障碍服务
        if (!ControlAccessibilityService.isServiceRunning()) {
            Toast.makeText(context, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            onOpenSettings()
            return
        }

        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            onCheckOverlayPermission()
            return
        }

        // 启动执行悬浮窗（Android 14+ 需要使用 startForegroundService）
        val intent = Intent(context, FloatingControlService::class.java).apply {
            putExtra(FloatingControlService.EXTRA_MODE, FloatingControlService.MODE_EXECUTE)
            putExtra(FloatingControlService.EXTRA_ACTION_ID, action.id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun deleteAction(action: SavedAction) {
        actionRepository.deleteAction(action.id)
        refreshActions()
        showDeleteConfirm = null
    }

    fun saveAction(name: String, actions_list: List<String>, loopMode: LoopMode, loopCount: Int) {
        val savedAction = SavedAction(
            name = name,
            actions = actions_list,
            loopMode = loopMode,
            loopCount = loopCount
        )
        actionRepository.saveAction(savedAction)
        refreshActions()
        showNewActionDialog = false
    }

    MainScreen(
        actions = actions,
        onRecordClick = { startRecording() },
        onSettingsClick = onOpenSettings,
        onActionClick = { action ->
            selectedAction = action
        },
        onExecuteClick = { action ->
            startExecuting(action)
        },
        onDeleteClick = { action ->
            showDeleteConfirm = action
        },
        onAddManualClick = {
            showNewActionDialog = true
        }
    )

    // 新建动作对话框
    if (showNewActionDialog) {
        NewActionDialog(
            onSave = { name, actionsList, loopMode, loopCount ->
                saveAction(name, actionsList, loopMode, loopCount)
            },
            onDismiss = { showNewActionDialog = false }
        )
    }

    // 动作详情对话框
    selectedAction?.let { action ->
        ActionDetailDialog(
            action = action,
            onDismiss = { selectedAction = null },
            onEdit = { updatedAction ->
                actionRepository.updateAction(updatedAction)
                refreshActions()
                selectedAction = null
            }
        )
    }

    // 删除确认对话框
    showDeleteConfirm?.let { action ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { androidx.compose.material3.Text("确认删除") },
            text = { androidx.compose.material3.Text("确定要删除「${action.name}」吗？") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { deleteAction(action) }
                ) {
                    androidx.compose.material3.Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteConfirm = null }
                ) {
                    androidx.compose.material3.Text("取消")
                }
            }
        )
    }
}

private fun startRecordFloatingWindow(
    context: android.content.Context,
    resultCode: Int,
    data: Intent
) {
    // 使用全局变量保存 MediaProjection 授权数据（解决 Intent 传递问题）
    FloatingControlService.pendingMediaProjectionResultCode = resultCode
    FloatingControlService.pendingMediaProjectionData = data

    // 启动悬浮窗服务（Android 14+ 需要使用 startForegroundService）
    val intent = Intent(context, FloatingControlService::class.java).apply {
        putExtra(FloatingControlService.EXTRA_MODE, FloatingControlService.MODE_RECORD)
    }
    ContextCompat.startForegroundService(context, intent)
}
