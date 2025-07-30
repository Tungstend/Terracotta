package net.burningtnt.terracotta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.wifi.WifiManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import net.burningtnt.terracotta.core.LanScanCallback
import net.burningtnt.terracotta.core.NativeBridge
import net.burningtnt.terracotta.service.ConnectionService
import net.burningtnt.terracotta.service.GuestConfig
import net.burningtnt.terracotta.service.REQUEST_CODE_VPN
import net.burningtnt.terracotta.service.pendingVpnGuestConfig
import java.net.DatagramSocket
import java.net.InetAddress

object RoomManager {

    private fun acquireMulticastLock(context: Context) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifiManager.createMulticastLock("terracotta-lan-scan")
        lock.setReferenceCounted(true)
        lock.acquire()
    }

    private fun getWifiIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo ?: return null

        val ipInt = dhcpInfo.ipAddress
        val ipBytes = byteArrayOf(
            (ipInt and 0xFF).toByte(),
            (ipInt shr 8 and 0xFF).toByte(),
            (ipInt shr 16 and 0xFF).toByte(),
            (ipInt shr 24 and 0xFF).toByte()
        )
        return try {
            InetAddress.getByAddress(ipBytes).hostAddress
        } catch (e: Exception) {
            null
        }
    }

    fun startHosting(ctx: Context, onCode: (String) -> Unit) {
        acquireMulticastLock(ctx)
        val ip = getWifiIpAddress(ctx)
        if (ip == null) {
            Log.d("LAN", "Failed to get ip")
            return
        }
        Log.d("LAN", "当前 WiFi IP: $ip")
        NativeBridge.startLanScan(ip, object : LanScanCallback {
            override fun onPortFound(port: Int) {
                NativeBridge.stopLanScan()
                val code = NativeBridge.generateInviteCode(port)
                val result = NativeBridge.parseInviteCode(code) ?: return
                val name = "terracotta-${result.roomId}"

                val svc = Intent(ctx, ConnectionService::class.java).apply {
                    putExtra("role", "host")
                    putExtra("network_name", name)
                    putExtra("secret", "secret")
                    putExtra("port", result.port)
                    putExtra("invite_code", code)
                }
                ctx.startService(svc)
                onCode(code)
            }
        })
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