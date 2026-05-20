package com.action2control.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
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
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        const val MODE_RECORD = "record"
        const val MODE_EXECUTE = "execute"

        private var instance: FloatingControlService? = null

        @JvmStatic
        fun isRunning(): Boolean = instance != null
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var actionRepository: ActionRepository? = null

    // 录制模式变量
    private var screenRecorder: ScreenRecorder? = null
    private var mediaProjectionResultCode = 0
    private var mediaProjectionData: Intent? = null
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
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        actionRepository = ActionRepository(this)
        createNotificationChannel()
        startForeground(1, buildNotification("悬浮窗控制服务运行中"))
        Log.d(TAG, "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_RECORD
        Log.d(TAG, "onStartCommand mode=$mode")

        when (mode) {
            MODE_RECORD -> {
                mediaProjectionResultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                mediaProjectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(EXTRA_DATA)
                }

                Log.d(TAG, "MediaProjection: resultCode=$mediaProjectionResultCode, data=${mediaProjectionData != null}")

                if (mediaProjectionData == null) {
                    Toast.makeText(this, "屏幕录制授权数据为空", Toast.LENGTH_LONG).show()
                    stopSelf()
                    return START_NOT_STICKY
                }

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
        Log.d(TAG, "showRecordFloatingWindow")

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

        val view = LayoutInflater.from(this).inflate(R.layout.floating_record, null)
        floatingView = view

        val btnStart = view.findViewById<Button>(R.id.btn_start)
        val btnPause = view.findViewById<Button>(R.id.btn_pause)
        val btnStop = view.findViewById<Button>(R.id.btn_stop)
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)

        btnStart.setOnClickListener {
            Log.d(TAG, "btnStart clicked, state=$recordState")
            if (recordState == RecordState.IDLE) {
                startRecording()
            }
        }

        btnPause.setOnClickListener {
            Log.d(TAG, "btnPause clicked, state=$recordState")
            when (recordState) {
                RecordState.RECORDING -> pauseRecording()
                RecordState.PAUSED -> resumeRecording()
                else -> {}
            }
        }

        btnStop.setOnClickListener {
            Log.d(TAG, "btnStop clicked, state=$recordState")
            if (recordState == RecordState.RECORDING || recordState == RecordState.PAUSED) {
                stopRecording()
            }
        }

        setupDrag(view)

        val params = createLayoutParams()
        windowManager?.addView(view, params)

        // 初始 UI 状态
        updateRecordUI()
    }

    private fun startRecording() {
        Log.d(TAG, "startRecording called")
        val recorder = screenRecorder
        val data = mediaProjectionData
        if (recorder == null || data == null) {
            Log.e(TAG, "recorder or data is null")
            Toast.makeText(this, "录制器未初始化", Toast.LENGTH_SHORT).show()
            return
        }

        val success = recorder.startRecording(mediaProjectionResultCode, data)
        if (success) {
            recordState = RecordState.RECORDING
            Log.d(TAG, "Recording started successfully")
        } else {
            Log.e(TAG, "startRecording returned false")
            Toast.makeText(this, "录制启动失败", Toast.LENGTH_SHORT).show()
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
        val view = floatingView ?: return
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)
        val btnStart = view.findViewById<Button>(R.id.btn_start)
        val btnPause = view.findViewById<Button>(R.id.btn_pause)
        val btnStop = view.findViewById<Button>(R.id.btn_stop)

        tvStatus.text = "分析中..."
        btnStart.visibility = View.GONE
        btnPause.visibility = View.GONE
        btnStop.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            analyzeAndSave(videoPath)
        }
    }

    private fun updateRecordUI() {
        val view = floatingView ?: return
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)
        val btnStart = view.findViewById<Button>(R.id.btn_start)
        val btnPause = view.findViewById<Button>(R.id.btn_pause)
        val btnStop = view.findViewById<Button>(R.id.btn_stop)

        when (recordState) {
            RecordState.IDLE -> {
                tvStatus.text = "准备录制"
                btnStart.visibility = View.VISIBLE
                btnPause.visibility = View.GONE
                btnStop.visibility = View.GONE
                btnStart.isEnabled = true
                btnPause.isEnabled = true
                btnStop.isEnabled = true
            }
            RecordState.RECORDING -> {
                tvStatus.text = "录制中..."
                btnStart.visibility = View.GONE
                btnPause.visibility = View.VISIBLE
                btnStop.visibility = View.VISIBLE
                btnPause.text = "暂停"
                btnStart.isEnabled = false
                btnPause.isEnabled = true
                btnStop.isEnabled = true
            }
            RecordState.PAUSED -> {
                tvStatus.text = "已暂停"
                btnStart.visibility = View.GONE
                btnPause.visibility = View.VISIBLE
                btnStop.visibility = View.VISIBLE
                btnPause.text = "继续"
                btnStart.isEnabled = false
                btnPause.isEnabled = true
                btnStop.isEnabled = true
            }
        }
    }

    private suspend fun analyzeAndSave(videoPath: String) {
        val view = floatingView ?: return
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)

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
        val view = LayoutInflater.from(this).inflate(R.layout.floating_execute, null)

        val tvName = view.findViewById<TextView>(R.id.tv_action_name)
        val tvLoop = view.findViewById<TextView>(R.id.tv_loop_info)
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)
        val btnPause = view.findViewById<Button>(R.id.btn_pause)
        val btnStop = view.findViewById<Button>(R.id.btn_stop)

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

        setupDrag(view)

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

            val view = floatingView ?: return@launch
            val tvStatus = view.findViewById<TextView>(R.id.tv_status)

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

    private fun setupDrag(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = (v.layoutParams as WindowManager.LayoutParams).x
                    initialY = (v.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val params = v.layoutParams as WindowManager.LayoutParams
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(v, params)
                    true
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
