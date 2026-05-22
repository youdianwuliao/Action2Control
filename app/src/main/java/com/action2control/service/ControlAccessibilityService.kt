package com.action2control.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class ControlAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "ControlAccessibilityService"
        const val EXTRA_ACTION_SEQUENCE = "action_sequence"

        private var instance: ControlAccessibilityService? = null

        @JvmStatic
        fun isServiceRunning(): Boolean = instance != null

        @JvmStatic
        fun getInstance(): ControlAccessibilityService? = instance
    }

    private var actionDispatcher: ActionDispatcher? = null
    private var currentForegroundPackage: String? = null
    private var currentForegroundAppName: String? = null

    /**
     * 获取当前前台 App 的包名
     */
    fun getCurrentForegroundPackage(): String? = currentForegroundPackage

    /**
     * 获取当前前台 App 的名称
     */
    fun getCurrentForegroundAppName(): String? = currentForegroundAppName

    /**
     * 执行单个动作（供外部调用）
     */
    fun executeSingleAction(action: String) {
        actionDispatcher?.dispatchAction(action)
    }

    /**
     * 启动指定的 App
     */
    fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                Log.i(TAG, "Launched app: $packageName")
            } else {
                Log.w(TAG, "No launch intent for package: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app: $packageName", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听窗口状态变化，获取当前前台 App
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null && packageName != "com.action2control") {
                currentForegroundPackage = packageName
                // 获取 App 名称
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    currentForegroundAppName = packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    currentForegroundAppName = packageName
                }
                Log.d(TAG, "Foreground app changed: $packageName ($currentForegroundAppName)")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
        instance = this

        val metrics = android.util.DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)

        actionDispatcher = ActionDispatcher(
            service = this,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        Log.i(TAG, "ActionDispatcher initialized, screen: ${metrics.widthPixels}x${metrics.heightPixels}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand received")

        val actions = intent?.getStringArrayListExtra(EXTRA_ACTION_SEQUENCE)
        if (actions.isNullOrEmpty()) {
            Log.w(TAG, "No actions provided in intent")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Log.i(TAG, "Received ${actions.size} actions: $actions")

        val dispatcher = actionDispatcher
        if (dispatcher == null) {
            Log.e(TAG, "ActionDispatcher not ready, service may still be initializing")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        executeActionsSequentially(dispatcher, actions)
        return START_NOT_STICKY
    }

    private fun executeActionsSequentially(dispatcher: ActionDispatcher, actions: List<String>) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        object : Runnable {
            var index = 0

            override fun run() {
                if (index >= actions.size) {
                    Log.i(TAG, "All ${actions.size} actions completed")
                    stopSelf()
                    return
                }

                val action = actions[index]
                Log.i(TAG, "Executing action [$index/${actions.size}]: $action")

                try {
                    dispatcher.dispatchAction(action)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing action '$action': ${e.message}", e)
                }

                index++

                handler.postDelayed(this, 500L)
            }
        }.also { handler.post(it) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Service unbinding")
        instance = null
        actionDispatcher = null
        return super.onUnbind(intent)
    }
}
