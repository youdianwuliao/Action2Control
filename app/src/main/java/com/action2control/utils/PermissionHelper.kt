package com.action2control.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 权限管理辅助类
 * 负责相机、录音权限请求以及无障碍服务引导
 */
object PermissionHelper {

    /**
     * 创建相机和录音权限请求的 Launcher
     */
    fun createPermissionLauncher(
        context: Context,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ): ActivityResultLauncher<Array<String>> {
        val requestPermissionLauncher =
            (context as androidx.activity.ComponentActivity).registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val cameraGranted = permissions[android.Manifest.permission.CAMERA] == true
                val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] == true

                if (cameraGranted && audioGranted) {
                    onGranted()
                } else {
                    onDenied()
                }
            }
        return requestPermissionLauncher
    }

    /**
     * 请求相机和录音权限
     */
    fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            )
        )
    }

    /**
     * 检查相机权限是否已授予
     */
    fun isCameraGranted(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查录音权限是否已授予
     */
    fun isAudioGranted(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查无障碍服务是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceName: String): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(serviceName)
    }

    /**
     * 获取跳转到无障碍服务设置页的 Intent
     */
    fun getAccessibilitySettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * 获取完整的无障碍服务组件名
     */
    fun getAccessibilityServiceComponentName(context: Context): String {
        return "${context.packageName}/com.action2control.service.ControlAccessibilityService"
    }
}
