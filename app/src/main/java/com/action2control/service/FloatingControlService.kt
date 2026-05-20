package com.action2control.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
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
    private var screenRecorder: ScreenRecorder? = null
    private var actionRepository: ActionRepository? = null

    private var mode = MODE_RECORD
    private var isRecording = false
    private var isPaused = false
    private var mediaProjectionResultCode = 0
    private var mediaProjectionData: Intent? = null
    private var lastVideoPath: String? = null

    private var executeJob: Job? = null
    private var currentAction: SavedAction? = null
    private var executeLoopCount = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        actionRepository = ActionRepository(this)
        createNotificationChannel()
        startForeground(1, buildNotification("悬浮窗控制服务运行中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_RECORD

        when (mode) {
            MODE_RECORD -> {
                mediaProjectionResultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                mediaProjectionData = intent?.getParcelableExtra(EXTRA_DATA)
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
    }

    // ==================== 录制模式悬浮窗 ====================

    private fun showRecordFloatingWindow() {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_record, null)

        val btnStart = view.findViewById<Button>(R.id.btn_start)
        val btnPause = view.findViewById<Button>(R.id.btn_pause)
        val btnStop = view.findViewById<Button>(R.id.btn_stop)
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)

        btnStart.setOnClickListener {
            if (!isRecording) {
                startRecording()
                btnStart.visibility = View.GONE
                btnPause.visibility = View.VISIBLE
                btnStop.visibility = View.VISIBLE
                tvStatus.text = "录制中..."
            }
        }

        btnPause.setOnClickListener {
            if (isRecording) {
                if (isPaused) {
                    screenRecorder?.resumeRecording()
                    isPaused = false
                    btnPause.text = "暂停"
                    tvStatus.text = "录制中..."
                } else {
                    screenRecorder?.pauseRecording()
                    isPaused = true
                    btnPause.text = "继续"
                    tvStatus.text = "已暂停"
                }
            }
        }

        btnStop.setOnClickListener {
            if (isRecording) {
                tvStatus.text = "停止中..."
                btnStart.isEnabled = false
                btnPause.isEnabled = false
                btnStop.isEnabled = false

                screenRecorder?.stopRecording()
                isRecording = false
                isPaused = false

                // 等待一点时间让回调完成
                Handler(Looper.getMainLooper()).postDelayed({
                    val videoPath = lastVideoPath
                    if (videoPath != null && videoPath.isNotEmpty()) {
                        tvStatus.text = "分析中..."
                        btnStart.visibility = View.GONE
                        btnPause.visibility = View.GONE
                        btnStop.visibility = View.GONE

                        CoroutineScope(Dispatchers.Main).launch {
                            analyzeAndSave(videoPath)
                        }
                    } else {
                        tvStatus.text = "未获取到视频"
                        Toast.makeText(this@FloatingControlService, "未获取到视频文件", Toast.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            removeFloatingWindow()
                            stopSelf()
                        }, 2000)
                    }
                }, 500)
            }
        }

        // 添加拖拽功能
        setupDrag(view)

        val params = createLayoutParams()
        windowManager?.addView(view, params)
        floatingView = view

        // 初始化录屏器
        screenRecorder = ScreenRecorder(
            context = this,
            onRecordingComplete = { videoPath ->
                lastVideoPath = videoPath
                Log.d(TAG, "Recording completed: $videoPath")
            },
            onRecordingError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun startRecording() {
        val recorder = screenRecorder ?: return
        if (recorder.startRecording(mediaProjectionResultCode, mediaProjectionData!!)) {
            isRecording = true
            isPaused = false
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

                withContext(Dispatchers.Main) {
                    tvStatus.text = "已保存 (${actionSequence.size} 个动作)"
                    Toast.makeText(this@FloatingControlService, "动作已保存", Toast.LENGTH_SHORT).show()

                    Handler(Looper.getMainLooper()).postDelayed({
                        removeFloatingWindow()
                        stopSelf()
                    }, 2000)
                }
            } else {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "未识别到动作"
                    Toast.makeText(this@FloatingControlService, "未识别到有效动作", Toast.LENGTH_SHORT).show()

                    Handler(Looper.getMainLooper()).postDelayed({
                        removeFloatingWindow()
                        stopSelf()
                    }, 2000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "分析失败", e)
            withContext(Dispatchers.Main) {
                tvStatus.text = "分析失败"
                Toast.makeText(this@FloatingControlService, "分析失败: ${e.message}", Toast.LENGTH_SHORT).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    removeFloatingWindow()
                    stopSelf()
                }, 2000)
            }
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
                if (isPaused) {
                    isPaused = false
                    btnPause.text = "暂停"
                    tvStatus.text = "执行中..."
                } else {
                    isPaused = true
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
                while (isPaused && isActive) {
                    delay(100)
                }

                if (!isActive) break

                withContext(Dispatchers.Main) {
                    tvStatus.text = "第 ${loopIndex + 1} 轮"
                }

                // 执行动作序列
                for ((index, act) in action.actions.withIndex()) {
                    while (isPaused && isActive) {
                        delay(100)
                    }
                    if (!isActive) break

                    service.executeSingleAction(act)
                    delay(800) // 动作间隔
                }

                loopIndex++
            }

            withContext(Dispatchers.Main) {
                val tvStatus = view.findViewById<TextView>(R.id.tv_status)
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
