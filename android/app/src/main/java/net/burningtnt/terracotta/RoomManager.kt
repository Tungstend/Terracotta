package net.burningtnt.terracotta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import net.burningtnt.terracotta.core.LanScanCallback
import net.burningtnt.terracotta.core.NativeBridge
import net.burningtnt.terracotta.service.ConnectionService
import net.burningtnt.terracotta.service.GuestConfig
import net.burningtnt.terracotta.service.HostConfig
import net.burningtnt.terracotta.service.pendingVpnGuestConfig
import net.burningtnt.terracotta.service.pendingVpnHostConfig
import java.net.DatagramSocket

object RoomManager {

    private fun acquireMulticastLock(context: Context) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifiManager.createMulticastLock("terracotta-lan-scan")
        lock.setReferenceCounted(true)
        lock.acquire()
    }

    fun startHosting(ctx: Activity, vpnLauncher: ActivityResultLauncher<Intent>, onCode: (String) -> Unit) {
        acquireMulticastLock(ctx)
        NativeBridge.startLanScan(object : LanScanCallback {
            override fun onPortFound(port: Int) {
                Handler(Looper.getMainLooper()).postDelayed({
                    NativeBridge.stopLanScan()
                }, 500)
                ctx.runOnUiThread {

                    val code = NativeBridge.generateInviteCode(port)
                    val result = NativeBridge.parseInviteCode(code)
                    if (result == null) {
                        Log.e("invite code", "邀请码无效")
                    } else {
//                        Log.d("invite code", "$code")
//                        Log.d("invite code", "terracotta-mc-${result.name.lowercase()}")
//                        Log.d("invite code", "${result.secret.lowercase()}")
                        val networkName = "terracotta-mc-${result.name.lowercase()}"
                        val secret = result.secret.lowercase()

                        val intent = VpnService.prepare(ctx)
                        if (intent != null) {
                            pendingVpnHostConfig = HostConfig(networkName, secret, code)
                            vpnLauncher.launch(intent)
                        } else {
                            // 已授权，可以直接启动服务
                            startHostVpnService(ctx, networkName, secret, code)
                        }

                        onCode(code)
                    }
                }
            }
        })
    }

    fun startHostVpnService(ctx: Context, networkName: String, secret: String, inviteCode: String) {
        val svc = Intent(ctx, ConnectionService::class.java).apply {
            putExtra("role", "host")
            putExtra("network_name", networkName)
            putExtra("secret", secret)
            putExtra("invite_code", inviteCode)
        }

        ctx.startService(svc)
    }

    fun joinRoom(ctx: Activity, vpnLauncher: ActivityResultLauncher<Intent>, code: String, onError: (String) -> Unit) {
        val result = NativeBridge.parseInviteCode(code)
        if (result == null) {
            onError("邀请码无效")
            return
        }

        val networkName = "terracotta-mc-${result.name.lowercase()}"
        val secret = result.secret.lowercase()
        val forwardPort = getRandomUdpPort()

        val intent = VpnService.prepare(ctx)
        if (intent != null) {
            pendingVpnGuestConfig = GuestConfig(networkName, secret, result.port, forwardPort)
            vpnLauncher.launch(intent)
        } else {
            // 已授权，可以直接启动服务
            startGuestVpnService(ctx, networkName, secret, result.port, forwardPort)
        }
    }

    fun startGuestVpnService(ctx: Context, networkName: String, secret: String, port: Int, forwardPort: Int) {
        val svc = Intent(ctx, ConnectionService::class.java).apply {
            putExtra("role", "guest")
            putExtra("network_name", networkName)
            putExtra("secret", secret)
            putExtra("port", port)
            putExtra("local_port", forwardPort)
        }

        ctx.startService(svc)
    }


    fun getRandomUdpPort(): Int {
        return try {
            DatagramSocket(0).use { socket ->
                socket.localPort
            }
        } catch (e: Exception) {
            35781
        }
    }

}