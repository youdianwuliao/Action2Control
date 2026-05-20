package com.action2control.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log

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
