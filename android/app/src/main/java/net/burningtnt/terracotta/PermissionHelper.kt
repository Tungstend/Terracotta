package net.burningtnt.terracotta

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PermissionHelper {

    private fun getRequiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()

        perms += "android.permission.FOREGROUND_SERVICE"

        if (Build.VERSION.SDK_INT >= 34) {
            perms += "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"
            perms += "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
        }
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }

        return perms.toTypedArray()
    }

    fun shouldRequestIgnoreBattery(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBattery(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = ("package:" + activity.packageName).toUri()
            activity.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun requestAllPermissions(activity: Activity, onDone: () -> Unit) {
        val missing = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(activity, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            onDone()
        } else {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), 321)
        }
    }

    fun handlePermissionResult(
        requestCode: Int, grantResults: IntArray, onSuccess: () -> Unit, onFail: () -> Unit
    ) {
        if (requestCode == 321 && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            onSuccess()
        } else {
            onFail()
        }
    }

    fun showPermissionExplainDialog(activity: Activity, onAccept: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("权限申请说明")
            .setMessage("""
            为了确保 Terracotta 联机功能正常工作，我们需要申请以下权限：
            
            • 前台服务权限（保证网络连接持续）
            • 设备连接标识（用于创建或加入房间）
            • 通知权限（用于展示连接状态）
            • 忽略电池优化（防止系统后台中断连接）
        """.trimIndent())
            .setCancelable(false)
            .setPositiveButton("我了解了") { _, _ -> onAccept() }
            .show()
    }

    fun requestAllWithExplanationIfNeeded(
        activity: Activity,
        onGranted: () -> Unit
    ) {
        val missing = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(activity, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            if (shouldRequestIgnoreBattery(activity)) {
                requestIgnoreBattery(activity)
            }
            onGranted()
        } else {
            showPermissionExplainDialog(activity) {
                ActivityCompat.requestPermissions(activity, missing.toTypedArray(), 321)
            }
        }
    }
}
