package com.action2control.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.content.pm.ServiceInfo
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.action2control.MainActivity
import com.action2control.R
import com.action2control.analyzeVideo
import com.action2control.camera.ScreenRecorder
import com.action2control.data.ActionRepository
import com.action2control.data.LoopMode
import com.action2control.data.SavedAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮窗控制服务
 * 支持两种模式：
 * 1. 录制模式：显示开始/暂停/停止按钮，控制屏幕录制
 * 2. 执行模式：显示暂停/停止按钮，循环执行动作序列
 */
class FloatingControlService : Service() {

    companion object {
        const val TAG = "FloatingControlService"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ACTION_ID = "action_id"

        const val MODE_RECORD = "record"
        const val MODE_EXECUTE = "execute"

        // 全局变量保存 MediaProjection 授权数据（解决 Intent 传递问题）
        @Volatile
        var pendingMediaProjectionResultCode = Activity.RESULT_CANCELED
        @Volatile
        var pendingMediaProjectionData: Intent? = null

        private var instance: FloatingControlService? = null

        @JvmStatic
        fun isRunning(): Boolean = instance != null
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var contentLayout: LinearLayout? = null // 保存 contentLayout 引用
    private var actionRepository: ActionRepository? = null

    // 录制模式变量
    private var screenRecorder: ScreenRecorder? = null
    private var recordState = RecordState.IDLE

    // 执行模式变量
    private var executeJob: Job? = null
    private var executePaused = false
    private var currentAction: SavedAction? = null

    // 枚举录制状态
    private enum class RecordState {
        IDLE,       // 未录制
        RECORDING,  // 正在录制
        PAUSED      // 已暂停
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate - initializing")
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        actionRepository = ActionRepository(this)
        createNotificationChannel()
        Log.d(TAG, "Service onCreate completed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_RECORD
        Log.d(TAG, "onStartCommand mode=$mode, intent extras: ${intent?.extras}")

        // Android 14+ 必须在 onStartCommand 中调用 startForeground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "Starting foreground service with MEDIA_PROJECTION type")
            startForeground(1, buildNotification("悬浮窗控制服务运行中"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            Log.d(TAG, "Starting foreground service (pre-Android 14)")
            startForeground(1, buildNotification("悬浮窗控制服务运行中"))
        }
        Log.d(TAG, "startForeground called successfully")

        when (mode) {
            MODE_RECORD -> {
                Log.d(TAG, "Switching to record mode")
                showRecordFloatingWindow()
            }
            MODE_EXECUTE -> {
                val actionId = intent?.getStringExtra(EXTRA_ACTION_ID) ?: ""
                val action = actionRepository?.getAllActions()?.find { it.id == actionId }
                if (action != null) {
                    currentAction = action
                    showExecuteFloatingWindow(action)
                    startExecuting(action)
                } else {
                    Toast.makeText(this, "动作未找到", Toast.LENGTH_SHORT).show()
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeFloatingWindow()
        screenRecorder?.release()
        executeJob?.cancel()
        Log.d(TAG, "Service onDestroy")
    }

    // ==================== 录制模式悬浮窗 ====================

    private fun showRecordFloatingWindow() {
        Log.d(TAG, "showRecordFloatingWindow called")

        // 从全局变量获取 MediaProjection 授权数据
        val resultCode = pendingMediaProjectionResultCode
        val data = pendingMediaProjectionData

        Log.d(TAG, "MediaProjection: resultCode=$resultCode, data=${data != null}")

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "MediaProjection authorization not available")
            Toast.makeText(this, "屏幕录制授权数据丢失，请重新尝试", Toast.LENGTH_LONG).show()
            // 不清理全局变量，允许重试
            return
        }

        // 初始化 ScreenRecorder
        screenRecorder = ScreenRecorder(
            context = this,
            onRecordingComplete = { videoPath ->
                Log.d(TAG, "onRecordingComplete: $videoPath")
                handleRecordingComplete(videoPath)
            },
            onRecordingError = { error ->
                Log.e(TAG, "onRecordingError: $error")
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                recordState = RecordState.IDLE
                updateRecordUI()
            }
        )

        // 使用 ContextThemeWrapper 确保按钮正确渲染
        val themedContext = ContextThemeWrapper(this, R.style.Theme_Action2Control)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.floating_record, null)
        floatingView = view

        // 获取拖拽区域和内容区域
        val dragHandle = view.findViewById<View>(R.id.drag_handle)
        contentLayout = view.findViewById(R.id.content_layout)

        // 获取按钮引用（在 contentLayout 中）
        val btnStart = contentLayout!!.findViewById<Button>(R.id.btn_start)
        val btnPause = contentLayout!!.findViewById<Button>(R.id.btn_pause)
        val btnStop = contentLayout!!.findViewById<Button>(R.id.btn_stop)
        val tvStatus = contentLayout!!.findViewById<TextView>(R.id.tv_status)

        Log.d(TAG, "Views: dragHandle=${dragHandle != null}, contentLayout=${contentLayout != null}")
        Log.d(TAG, "Buttons: btnStart=${btnStart != null}, btnPause=${btnPause != null}, btnStop=${btnStop != null}")

        // 设置按钮点击事件
        btnStart.setOnClickListener {
            Log.d(TAG, "btnStart CLICKED, state=$recordState")
            if (recordState == RecordState.IDLE) {
                startRecording(resultCode, data)
            }
        }

        btnPause.setOnClickListener {
            Log.d(TAG, "btnPause CLICKED, state=$recordState")
            when (recordState) {
                RecordState.RECORDING -> pauseRecording()
                RecordState.PAUSED -> resumeRecording()
                else -> {}
            }
        }

        btnStop.setOnClickListener {
            Log.d(TAG, "btnStop CLICKED, state=$recordState")
            if (recordState == RecordState.RECORDING || recordState == RecordState.PAUSED) {
                stopRecording()
            }
        }

        // 只在拖拽区域处理拖拽，不影响按钮点击
        setupDragOnView(dragHandle)

        // 添加到窗口
        try {
            val params = createLayoutParams()
            windowManager?.addView(view, params)
            Log.d(TAG, "Floating window added to WindowManager successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating window", e)
            Toast.makeText(this, "悬浮窗添加失败: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // 初始 UI 状态
        updateRecordUI()
        Log.d(TAG, "showRecordFloatingWindow completed")
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        Log.d(TAG, "startRecording called")
        val recorder = screenRecorder
        if (recorder == null) {
            Log.e(TAG, "screenRecorder is null")
            Toast.makeText(this, "录制器未初始化", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val success = recorder.startRecording(resultCode, data)
            if (success) {
                recordState = RecordState.RECORDING
                Log.d(TAG, "Recording started successfully")
            } else {
                Log.e(TAG, "startRecording returned false")
                Toast.makeText(this, "录制启动失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "startRecording exception", e)
            Toast.makeText(this, "录制异常: ${e.message}", Toast.LENGTH_LONG).show()
        }
        updateRecordUI()
    }

    private fun pauseRecording() {
        Log.d(TAG, "pauseRecording called")
        screenRecorder?.pauseRecording()
        recordState = RecordState.PAUSED
        updateRecordUI()
    }

    private fun resumeRecording() {
        Log.d(TAG, "resumeRecording called")
        screenRecorder?.resumeRecording()
        recordState = RecordState.RECORDING
        updateRecordUI()
    }

    private fun stopRecording() {
        Log.d(TAG, "stopRecording called")
        recordState = RecordState.IDLE
        updateRecordUI()
        screenRecorder?.stopRecording()
        // onRecordingComplete 回调会处理后续分析
    }

    private fun handleRecordingComplete(videoPath: String) {
        Log.d(TAG, "handleRecordingComplete: $videoPath")
        val layout = contentLayout ?: return
        
        val tvStatus = layout.findViewById<TextView>(R.id.tv_status)
        val btnStart = layout.findViewById<Button>(R.id.btn_start)
        val btnPause = layout.findViewById<Button>(R.id.btn_pause)
        val btnStop = layout.findViewById<Button>(R.id.btn_stop)

        tvStatus.text = "分析中..."
        btnStart.visibility = View.GONE
        btnPause.visibility = View.GONE
        btnStop.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            analyzeAndSave(videoPath)
        }
    }

    private fun updateRecordUI() {
        val layout = contentLayout ?: return
        val tvStatus = layout.findViewById<TextView>(R.id.tv_status)
        val btnStart = layout.findViewById<Button>(R.id.btn_start)
        val btnPause = layout.findViewById<Button>(R.id.btn_pause)
        val btnStop = layout.findViewById<Button>(R.id.btn_stop)

        Log.d(TAG, "updateRecordUI: state=$recordState")

        when (recordState) {
            RecordState.IDLE -> {
                tvStatus.text = "准备录制"
                btnStart.visibility = View.VISIBLE
                btnPause.visibility = View.GONE
                btnStop.visibility = View.GONE
                btnStart.isEnabled = true
            }
            RecordState.RECORDING -> {
                tvStatus.text = "录制中..."
                btnStart.visibility = View.GONE
                btnPause.visibility = View.VISIBLE
                btnStop.visibility = View.VISIBLE
                btnPause.text = "暂停"
            }
            RecordState.PAUSED -> {
                tvStatus.text = "已暂停"
                btnStart.visibility = View.GONE
                btnPause.visibility = View.VISIBLE
                btnStop.visibility = View.VISIBLE
                btnPause.text = "继续"
            }
        }
    }

    private suspend fun analyzeAndSave(videoPath: String) {
        val layout = contentLayout ?: return
        val tvStatus = layout.findViewById<TextView>(R.id.tv_status)

        try {
            val actionSequence = analyzeVideo(this, videoPath) { current, total, phase ->
                Handler(Looper.getMainLooper()).post {
                    tvStatus.text = "$phase: $current/$total"
                }
            }

            if (actionSequence.isNotEmpty()) {
                val savedAction = SavedAction(
                    name = "动作序列 ${System.currentTimeMillis()}",
                    actions = actionSequence,
                    videoPath = videoPath
                )
                actionRepository?.saveAction(savedAction)

                Log.i(TAG, "Saved action: ${savedAction.name}, actions: $actionSequence")

                tvStatus.text = "已保存 (${actionSequence.size} 个动作)"
                Toast.makeText(this, "动作已保存", Toast.LENGTH_SHORT).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    removeFloatingWindow()
                    stopSelf()
                }, 2000)
            } else {
                tvStatus.text = "未识别到动作"
                Toast.makeText(this, "未识别到有效动作", Toast.LENGTH_SHORT).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    removeFloatingWindow()
                    stopSelf()
                }, 2000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "分析失败", e)
            tvStatus.text = "分析失败"
            Toast.makeText(this, "分析失败: ${e.message}", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                removeFloatingWindow()
                stopSelf()
            }, 2000)
        }
    }

    // ==================== 执行模式悬浮窗 ====================

    private fun showExecuteFloatingWindow(action: SavedAction) {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_Action2Control)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.floating_execute, null)

        val dragHandle = view.findViewById<View>(R.id.drag_handle)
        contentLayout = view.findViewById(R.id.content_layout)

        val tvName = contentLayout!!.findViewById<TextView>(R.id.tv_action_name)
        val tvLoop = contentLayout!!.findViewById<TextView>(R.id.tv_loop_info)
        val tvStatus = contentLayout!!.findViewById<TextView>(R.id.tv_status)
        val btnPause = contentLayout!!.findViewById<Button>(R.id.btn_pause)
        val btnStop = contentLayout!!.findViewById<Button>(R.id.btn_stop)

        tvName.text = action.name
        tvLoop.text = when (action.loopMode) {
            LoopMode.INFINITE -> "无限循环"
            LoopMode.COUNT -> "循环 ${action.loopCount} 次"
        }

        btnPause.setOnClickListener {
            if (executeJob?.isActive == true) {
                if (executePaused) {
                    executePaused = false
                    btnPause.text = "暂停"
                    tvStatus.text = "执行中..."
                } else {
                    executePaused = true
                    btnPause.text = "继续"
                    tvStatus.text = "已暂停"
                }
            }
        }

        btnStop.setOnClickListener {
            executeJob?.cancel()
            removeFloatingWindow()
            stopSelf()
        }

        // 只在拖拽区域处理拖拽
        setupDragOnView(dragHandle)

        val params = createLayoutParams()
        windowManager?.addView(view, params)
        floatingView = view
    }

    private fun startExecuting(action: SavedAction) {
        executePaused = false
        executeJob = CoroutineScope(Dispatchers.Default).launch {
            val service = ControlAccessibilityService.getInstance()
            if (service == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingControlService, "无障碍服务未开启", Toast.LENGTH_SHORT).show()
                    removeFloatingWindow()
                    stopSelf()
                }
                return@launch
            }

            val layout = contentLayout ?: return@launch
            val tvStatus = layout.findViewById<TextView>(R.id.tv_status)

            var loopIndex = 0
            val maxLoops = if (action.loopMode == LoopMode.INFINITE) Int.MAX_VALUE else action.loopCount

            while (loopIndex < maxLoops && isActive) {
                // 等待暂停恢复
                while (executePaused && isActive) {
                    delay(100)
                }

                if (!isActive) break

                withContext(Dispatchers.Main) {
                    tvStatus.text = "第 ${loopIndex + 1} 轮"
                }

                // 执行动作序列
                for (act in action.actions) {
                    while (executePaused && isActive) {
                        delay(100)
                    }
                    if (!isActive) break

                    service.executeSingleAction(act)
                    delay(800) // 动作间隔
                }

                loopIndex++
            }

            withContext(Dispatchers.Main) {
                tvStatus.text = "执行完成"
                Toast.makeText(this@FloatingControlService, "执行完成", Toast.LENGTH_SHORT).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    removeFloatingWindow()
                    stopSelf()
                }, 2000)
            }
        }
    }

    // ==================== 通用方法 ====================

    /**
     * 在指定 view 上设置拖拽功能
     * 只在拖拽区域处理拖拽，不影响其他区域的点击事件
     */
    private fun setupDragOnView(dragView: View) {
        var startX = 0f
        var startY = 0f
        var initialX = 0
        var initialY = 0
        var isDragging = false
        val dragThreshold = 10f

        dragView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialX = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                    initialY = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                    isDragging = false
                    true // 在拖拽区域拦截事件
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    
                    if (!isDragging && (kotlin.math.abs(dx) > dragThreshold || kotlin.math.abs(dy) > dragThreshold)) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                        if (params != null) {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager?.updateViewLayout(floatingView!!, params)
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isDragging
                }
                else -> false
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 100
        }
    }

    private fun removeFloatingWindow() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Remove view error", e)
            }
        }
        floatingView = null
        contentLayout = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floating_control",
                "悬浮窗控制",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "floating_control")
            .setContentTitle("Action2Control")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }
}
