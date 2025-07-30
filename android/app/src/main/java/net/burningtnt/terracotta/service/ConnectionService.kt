package net.burningtnt.terracotta.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import net.burningtnt.terracotta.R
import net.burningtnt.terracotta.core.NativeBridge

class ConnectionService : Service() {

    private var lanBroadcastListener: LanBroadcastListener? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

                    // 启动 LAN 广播监听器 👇
                    lanBroadcastListener = LanBroadcastListener(this)
                    lanBroadcastListener?.startListening { data ->
                        //Log.d("LanBroadcast", "📡 收到广播: $data")

                        val portRegex = Regex("AD(\\d+)")
                        val match = portRegex.find(data)
                        val lanPort = match?.groups?.get(1)?.value?.toIntOrNull()

                        if (lanPort != null) {
                            Log.i("LanBroadcast", "✅ Minecraft 房主开放端口: $lanPort")
                            // 可以在此触发 UI 通知或自动加入逻辑
                        }
                    }

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
        super.onDestroy()
        lanBroadcastListener?.stopListening()
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
