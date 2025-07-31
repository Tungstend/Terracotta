package net.burningtnt.terracotta.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import net.burningtnt.terracotta.R
import net.burningtnt.terracotta.core.NativeBridge

class ConnectionService : VpnService() {

    private var vpnPFD: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, createNotification("guest", 55678, null)) // 或任意 placeholder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
            .setSession("EasyTier VPN")
            .setMtu(1300)
            .setBlocking(false)
            .addAddress("10.144.144.2", 24)
            .addRoute("10.144.144.0", 24)

        try {
            builder.addDisallowedApplication("net.burningtnt.terracotta")
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        val vpnInterface = builder.establish() ?: throw Exception("VPN 创建失败")
        val tunFd = vpnInterface.fileDescriptor

        val role = intent?.getStringExtra("role") ?: return START_NOT_STICKY
        val networkName = intent.getStringExtra("network_name") ?: return START_NOT_STICKY
        val secret = intent.getStringExtra("secret") ?: "secret"
        val port = intent.getIntExtra("port", 25565)
        val forwardPort = intent.getIntExtra("local_port", 55678)

        startForeground(NOTIF_ID, createNotification(role, forwardPort, intent.getStringExtra("invite_code")))

        val logDir = filesDir.absolutePath

        Thread {
            try {
                val i: Int;
                if (role == "host") {
                    i = NativeBridge.startEasyTierHost(networkName, secret, port, logDir)
                } else {
                    i = NativeBridge.startEasyTierGuest(networkName, secret, forwardPort, port, logDir)
                    Thread.sleep(5000)
                    val pfd = ParcelFileDescriptor.dup(tunFd)
                    vpnPFD = pfd
                    val result = NativeBridge.setTunFd("Terracotta-Guest", pfd)
                    if (result != 0) {
                        Log.e("EasyTier", "❌ setTunFd failed")
                    } else {
                        Log.i("EasyTier", "✅ setTunFd success")
                    }
                    Thread {
                        while (true) {
                            val retainResult = NativeBridge.retainNetworkInstance(arrayOf("Terracotta-Guest"))
                            Log.i("EasyTier", "retainNetworkInstance result = $retainResult")
                            Thread.sleep(10000)
                        }
                    }.start()
                    NativeBridge.startFakeServer("陶瓦大厅", forwardPort)
                }
                Log.d("EasyTier code", "EasyTier start with code: $i")
            } catch (e: Exception) {
                Log.e("ConnectionService", "启动失败: ${e.message}", e)
                stopSelf()
            }
        }.start()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        vpnPFD?.close()
        vpnPFD = null
        stopForeground(true)
        NativeBridge.retainNetworkInstance(emptyArray())
        super.onDestroy()
    }

    private fun createNotification(role: String, forwardPort: Int, inviteCode: String?): Notification {
        val channelId = "terracotta_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Terracotta 联机", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        val builder = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.terracotta)
            .setContentTitle("Terracotta 正在运行")
            .setContentText(if (role == "host") "房主模式运行中..." else "访客已连接")
            .setOngoing(true)

        // 通知按钮
        val exitIntent = Intent(this, ConnectionControlReceiver::class.java)
            .setAction("ACTION_EXIT")
        val exitPending = PendingIntent.getBroadcast(this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE)

        builder.addAction(Notification.Action.Builder(
            null, "退出房间", exitPending
        ).build())

        if (role == "host" && inviteCode != null) {
            val copyIntent = Intent(this, ConnectionControlReceiver::class.java)
                .setAction("ACTION_COPY_INVITE_CODE")
                .putExtra("invite_code", inviteCode)
            val copyPending = PendingIntent.getBroadcast(this, 2, copyIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(
                null, "复制邀请码", copyPending
            ).build())
        } else {
            val copyIntent = Intent(this, ConnectionControlReceiver::class.java)
                .setAction("ACTION_COPY_SERVER")
                .putExtra("server", "127.0.0.1:" + forwardPort)
            val copyPending = PendingIntent.getBroadcast(this, 3, copyIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(
                null, "复制服务器地址", copyPending
            ).build())
        }

        return builder.build()
    }

    companion object {
        const val NOTIF_ID = 9981
    }
}
