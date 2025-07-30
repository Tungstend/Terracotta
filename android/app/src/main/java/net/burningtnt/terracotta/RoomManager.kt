package net.burningtnt.terracotta

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import android.widget.Toast
import net.burningtnt.terracotta.core.LanScanCallback
import net.burningtnt.terracotta.core.NativeBridge
import net.burningtnt.terracotta.service.ConnectionService
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
        Toast.makeText(ctx, "start", Toast.LENGTH_SHORT).show()
        acquireMulticastLock(ctx)
        val ip = getWifiIpAddress(ctx)
        if (ip == null) {
            Log.d("LAN", "Failed to get ip")
            return
        }
        Log.d("LAN", "当前 WiFi IP: $ip")
        NativeBridge.startLanScan(ip, object : LanScanCallback {
            override fun onPortFound(port: Int) {
                Toast.makeText(ctx, port, Toast.LENGTH_SHORT).show()
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

    fun joinRoom(ctx: Context, code: String, onError: (String) -> Unit) {
        val result = NativeBridge.parseInviteCode(code)
        if (result == null) {
            onError("邀请码无效")
            return
        }

        val networkName = "terracotta-mc-${result.name.lowercase()}"
        val secret = result.secret.lowercase()
        val forwardPort = getRandomUdpPort()

        val svc = Intent(ctx, ConnectionService::class.java).apply {
            putExtra("role", "guest")
            putExtra("network_name", networkName)
            putExtra("secret", secret)
            putExtra("port", result.port)
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