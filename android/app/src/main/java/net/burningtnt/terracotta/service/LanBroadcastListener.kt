package net.burningtnt.terracotta.service

import android.content.Context
import android.net.wifi.WifiManager
import java.net.*

class LanBroadcastListener(private val context: Context) {
    private var multicastLock: WifiManager.MulticastLock? = null
    private var socket: MulticastSocket? = null
    private val group = InetAddress.getByName("224.0.2.60")
    private val port = 4445

    fun startListening(onReceive: (String) -> Unit) {
        // 获取 MulticastLock，避免 Android 阻止多播接收
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("terracotta-lan")
        multicastLock?.setReferenceCounted(true)
        multicastLock?.acquire()

        Thread {
            try {
                socket = MulticastSocket(port).apply {
                    reuseAddress = true
                    joinGroup(group)
                }

                val buffer = ByteArray(1024)
                while (!Thread.interrupted()) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket!!.receive(packet)

                    val data = String(packet.data, 0, packet.length)
                    onReceive(data)  // 回调收到的数据
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopListening()
            }
        }.start()
    }

    fun stopListening() {
        socket?.leaveGroup(group)
        socket?.close()
        socket = null

        multicastLock?.release()
        multicastLock = null
    }
}
