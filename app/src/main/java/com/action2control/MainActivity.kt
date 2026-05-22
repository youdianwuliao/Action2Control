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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.action2control.data.ActionRepository
import com.action2control.data.LoopMode
import com.action2control.data.SavedAction
import com.action2control.ml.ActionClassifier
import com.action2control.ml.PoseEstimator
import com.action2control.service.ControlAccessibilityService
import com.action2control.service.FloatingControlService
import com.action2control.ui.ActionDetailDialog
import com.action2control.ui.MainScreen
import com.action2control.ui.NewActionDialog
import kotlinx.coroutines.runBlocking

/**
 * 前置条件检查结果
 */
data class PreconditionResult(
    val isMet: Boolean,
    val missingItems: List<String>
)

/**
 * 检查所有前置条件
 */
fun checkPreconditions(context: android.content.Context): PreconditionResult {
    val missing = mutableListOf<String>()
    
    // 1. 无障碍服务
    if (!ControlAccessibilityService.isServiceRunning()) {
        missing.add("无障碍服务未开启")
    }
    
    // 2. 悬浮窗权限
    if (!Settings.canDrawOverlays(context)) {
        missing.add("悬浮窗权限未授权")
    }
    
    // 3. MediaPipe/TFLite 引擎检查
    try {
        runBlocking {
            val poseEstimator = PoseEstimator(context)
            val poseOk = poseEstimator.initialize()
            poseEstimator.close()
            if (!poseOk) {
                missing.add("姿态估计引擎不可用 (设备架构可能不支持)")
            }
            
            val classifier = ActionClassifier(context)
            val modelOk = classifier.loadModel()
            classifier.close()
            if (!modelOk) {
                missing.add("动作分类模型加载失败")
            }
        }
    } catch (e: Exception) {
        missing.add("AI 引擎初始化异常: ${e.message}")
    }
    
    return PreconditionResult(missing.isEmpty(), missing)
}

/**
 * 主 Activity
 * 动作库管理 + 录制/执行入口
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var actionRepository: ActionRepository
    private var preconditionResult by mutableStateOf<PreconditionResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionRepository = ActionRepository(this)
        preconditionResult = checkPreconditions(this)

        setContent {
            MaterialTheme {
                val currentPreconditions = preconditionResult
                if (currentPreconditions != null) {
                    AppContent(
                        actionRepository = actionRepository,
                        preconditions = currentPreconditions,
                        onRecheck = {
                            preconditionResult = checkPreconditions(this@MainActivity)
                        },
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
}

@Composable
fun AppContent(
    actionRepository: ActionRepository,
    preconditions: PreconditionResult,
    onRecheck: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 监听 Lifecycle，当 App 回到前台时自动检查前置条件
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("AppContent", "Lifecycle ON_RESUME -> Rechecking preconditions")
                onRecheck()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 如果前置条件不满足，显示强制遮罩
    if (!preconditions.isMet) {
        PreconditionBlocker(
            missingItems = preconditions.missingItems,
            onGoToAccessibility = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onGoToOverlay = {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        )
        return
    }

    // 满足条件，正常显示主界面
    MainAppContent(actionRepository, onOpenSettings, onCheckOverlayPermission)
}

@Composable
fun PreconditionBlocker(
    missingItems: List<String>,
    onGoToAccessibility: () -> Unit,
    onGoToOverlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "需要满足以下前置条件",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                missingItems.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("×", color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(item, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "请开启所需权限后返回本页面",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (missingItems.any { it.contains("无障碍") }) {
                        Button(
                            onClick = onGoToAccessibility,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("开启无障碍")
                        }
                    }
                    if (missingItems.any { it.contains("悬浮窗") }) {
                        Button(
                            onClick = onGoToOverlay,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("授权悬浮窗")
                        }
                    }
                    Button(
                        onClick = { /* Just refresh, will trigger onRecheck via lifecycle or user can reopen app */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("已设置，重试")
                    }
                }
            }
        }
        
        if (missingItems.any { it.contains("引擎") || it.contains("模型") }) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "提示：部分 AI 引擎不支持 x86_64 架构的模拟器，请使用 ARM 真机测试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MainAppContent(
    actionRepository: ActionRepository,
    onOpenSettings: () -> Unit,
    onCheckOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    var actions by remember { mutableStateOf(actionRepository.getAllActions()) }
    var showNewActionDialog by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf<SavedAction?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<SavedAction?>(null) }

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
        // 再次确保权限还在
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

        // 启动执行悬浮窗
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
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${action.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = { deleteAction(action) }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = null }
                ) {
                    Text("取消")
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
    // 使用全局变量保存 MediaProjection 授权数据
    FloatingControlService.pendingMediaProjectionResultCode = resultCode
    FloatingControlService.pendingMediaProjectionData = data

    // 启动悬浮窗服务
    val intent = Intent(context, FloatingControlService::class.java).apply {
        putExtra(FloatingControlService.EXTRA_MODE, FloatingControlService.MODE_RECORD)
    }
    ContextCompat.startForegroundService(context, intent)
}
