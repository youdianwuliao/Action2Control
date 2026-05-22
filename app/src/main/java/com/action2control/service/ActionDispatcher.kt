package com.action2control.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ActionDispatcher(
    private val service: AccessibilityService,
    private val screenWidth: Int,
    private val screenHeight: Int
) {

    private val tag = "ActionDispatcher"

    fun dispatchAction(action: String) {
        Log.i(tag, "Dispatching action: $action")
        when (action) {
            "swipe_up" -> executeSwipeUp()
            "swipe_down" -> executeSwipeDown()
            "like" -> executeTapCenter()
            "back" -> executeBack()
            else -> Log.w(tag, "Unknown action: $action")
        }
    }

    /**
     * 异步执行手势 (基于 suspendCoroutine)
     * 等待手势完成后再返回
     */
    suspend fun dispatchGestureAsync(gesture: GestureDescription): Boolean = suspendCancellableCoroutine { cont ->
        val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                cont.resume(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                cont.resume(false)
            }
        }
        service.dispatchGesture(gesture, callback, Handler(Looper.getMainLooper()))
    }

    /**
     * 异步执行滑动手势
     */
    suspend fun executeSwipeAsync(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        duration: Long = 300L
    ): Boolean {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }

        val stroke = StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGestureAsync(gesture).also { success ->
            if (success) {
                Log.i(tag, "Swipe gesture completed")
            } else {
                Log.e(tag, "Swipe gesture cancelled")
            }
        }
    }

    /**
     * 异步执行点击手势
     */
    suspend fun executeTapAsync(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }

        val stroke = StrokeDescription(path, 0L, 100L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGestureAsync(gesture).also { success ->
            if (success) {
                Log.i(tag, "Tap gesture completed")
            } else {
                Log.e(tag, "Tap gesture cancelled")
            }
        }
    }

    private fun executeSwipeUp() {
        val centerX = screenWidth / 2
        val startY = (screenHeight * 2 / 3).toFloat()
        val endY = (screenHeight / 3).toFloat()
        Log.d(tag, "Swipe up: ($centerX, $startY) -> ($centerX, $endY)")
        executeSwipe(centerX.toFloat(), startY, centerX.toFloat(), endY)
    }

    private fun executeSwipeDown() {
        val centerX = screenWidth / 2
        val startY = (screenHeight / 3).toFloat()
        val endY = (screenHeight * 2 / 3).toFloat()
        Log.d(tag, "Swipe down: ($centerX, $startY) -> ($centerX, $endY)")
        executeSwipe(centerX.toFloat(), startY, centerX.toFloat(), endY)
    }

    private fun executeTapCenter() {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        Log.d(tag, "Tap center: ($centerX, $centerY)")
        executeTap(centerX, centerY)
    }

    private fun executeBack() {
        Log.d(tag, "Executing global back action")
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun executeSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long = 300L) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }

        val stroke = StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                Log.i(tag, "Swipe gesture completed")
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                Log.e(tag, "Swipe gesture cancelled")
            }
        }
        service.dispatchGesture(gesture, callback, Handler(Looper.getMainLooper()))
    }

    fun executeTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }

        val stroke = StrokeDescription(path, 0L, 100L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                Log.i(tag, "Tap gesture completed")
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                Log.e(tag, "Tap gesture cancelled")
            }
        }
        service.dispatchGesture(gesture, callback, Handler(Looper.getMainLooper()))
    }
}
