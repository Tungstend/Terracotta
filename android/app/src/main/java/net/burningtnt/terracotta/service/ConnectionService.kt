package net.burningtnt.terracotta.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import net.burningtnt.terracotta.R
import net.burningtnt.terracotta.core.NativeBridge
import net.burningtnt.terracotta.core.RoomKind
import androidx.core.net.toUri

class ConnectionService : VpnService() {

    private var vpnInterfacePfd: ParcelFileDescriptor? = null // 原始 establish() 返回
    private var tunDupPfd: ParcelFileDescriptor? = null       // 传给 Native 的 dup FD
    private var keepAliveThread: Thread? = null
    private var roleTag: String = "" // "Terracotta-Host" / "Terracotta-Guest"
    @Volatile private var stopping = false

    private var currentRole: String = ""
    private var currentForwardPort: Int = 0
    private var currentInviteCode: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            "ACTION_REPOST_NOTIFICATION" -> {
                // 用当前状态重建并刷新前台通知
                // 注意：这里需要你能拿到 role / forwardPort / inviteCode 等当前配置
                val notif = createNotification(currentRole, currentForwardPort, currentInviteCode)
                // 方式一：如果前台状态意外丢失，直接再调用 startForeground
                startForeground(NOTIF_ID, notif)

                // 方式二：若仍在前台，仅需重新 notify（两者选其一）
                // val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                // manager.notify(NOTIF_ID, notif)

                return START_NOT_STICKY
            }
            "ACTION_STOP_VPN" -> {
                stopVpn() // 上条回答已提供封装
                return START_NOT_STICKY
            }
        }

        val role = intent?.getStringExtra("role") ?: return START_NOT_STICKY
        roleTag = if (role == "host") "Terracotta-Host" else "Terracotta-Guest"
        val networkName = intent.getStringExtra("network_name") ?: return START_NOT_STICKY
        val secret = intent.getStringExtra("secret") ?: "secret"
        val port = intent.getIntExtra("port", 25565)
        val forwardPort = intent.getIntExtra("local_port", 55678)
        val roomKind: RoomKind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("room_kind", RoomKind::class.java) ?: RoomKind.INVALID
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("room_kind") as? RoomKind ?: RoomKind.INVALID
        }

        val builder = Builder()
            .setSession("EasyTier VPN")
            .setMtu(1300)
            .setBlocking(false)
            .addRoute("10.144.144.0", 24)

        val guest_ipv4 = pickGuestIpV4(getDeviceUUID(this), roomKind, networkName, secret);

        if (role.equals("host"))
            builder.addAddress("10.144.144.1", 24)
        else
            builder.addAddress(guest_ipv4, 24)

        try { builder.addDisallowedApplication("net.burningtnt.terracotta") } catch (_: Exception) {}
        val pfd = builder.establish() ?: throw Exception("VPN 创建失败")
        vpnInterfacePfd = pfd
        val tunFd = pfd.fileDescriptor

        currentRole = role
        currentForwardPort = forwardPort
        currentInviteCode = intent.getStringExtra("invite_code")

        startForeground(NOTIF_ID, createNotification(role, forwardPort, intent.getStringExtra("invite_code")))

        Log.d("InviteCode", intent.getStringExtra("invite_code") ?: "null")

        val logDir = filesDir.absolutePath

        Thread {
            try {
                val code = if (role == "host") {
                    NativeBridge.startEasyTierHost(networkName, secret, logDir)
                } else {
                    NativeBridge.startEasyTierGuest(networkName, secret, forwardPort, port, roomKind, guest_ipv4, logDir)
                }

                // 等待 EasyTier 初始化完成（避免固定 sleep 5s，可以保留但最好有超时与停止检查）
                Thread.sleep(1500)
                if (stopping) return@Thread

                // 复制 FD 交给 Native
                tunDupPfd = ParcelFileDescriptor.dup(tunFd)
                val setRet = NativeBridge.setTunFd(roleTag, tunDupPfd!!)
                if (setRet != 0) Log.e("EasyTier", "❌ setTunFd failed") else Log.i("EasyTier", "✅ setTunFd success")

                // 启动保活线程（可选）：改成受控线程，能在 stop 时退出
                keepAliveThread = Thread {
                    while (!stopping) {
                        val retainResult = NativeBridge.retainNetworkInstance(arrayOf(roleTag))
                        Log.i("EasyTier", "retainNetworkInstance result = $retainResult")
                        try { Thread.sleep(10_000) } catch (_: InterruptedException) {}
                    }
                }.also { it.start() }

                if (role != "host") {
                    // 访客才开“大厅”
                    NativeBridge.startFakeServer("联机大厅", forwardPort)
                }

                Log.d("EasyTier", "Start code = $code")
            } catch (e: Exception) {
                Log.e("ConnectionService", "启动失败: ${e.message}", e)
                stopVpn()
            }
        }.start()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onRevoke() {
        // 系统/用户从 VPN 开关断开
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        // 双重保险：若还有资源未关，继续关
        stopVpn()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!stopping && vpnInterfacePfd != null) {
            // 兜底：通知被系统清了或任务被移除时，立刻重顶前台通知
            val notif = createNotification(currentRole, currentForwardPort, currentInviteCode)
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun stopVpn() {
        if (stopping) return
        stopping = true

        NativeBridge.stopFakeServer()

        // 先停保活线程与“大厅”
        keepAliveThread?.interrupt()
        keepAliveThread = null
        try {
            // 如果 Native 有 stop API，在此调用（示例名，按你 NativeBridge 实际函数替换）
            // NativeBridge.stopFakeServer()
            // NativeBridge.stopNetworkInstance(roleTag)
            // 如果没有 stop API，至少把 retain 置空让 native 不再保活：
            NativeBridge.retainNetworkInstance(emptyArray())
        } catch (_: Throwable) {}

        // 关闭 Tun FD（先关交给 Native 的 dup，再关原始 establish 的）
        try { tunDupPfd?.close() } catch (_: Throwable) {}
        tunDupPfd = null

        try { vpnInterfacePfd?.close() } catch (_: Throwable) {}
        vpnInterfacePfd = null

        // 完全移除通知
        stopForeground(STOP_FOREGROUND_REMOVE)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIF_ID)

        stopSelf()
    }

    private fun createNotification(role: String, forwardPort: Int, inviteCode: String?): Notification {
        val channelId = "terracotta_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "联机", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        // ConnectionService.kt -> createNotification(...)
        val deleteIntent = Intent(this, ConnectionControlReceiver::class.java)
            .setAction("ACTION_NOTIF_DELETED")
        val deletePending = PendingIntent.getBroadcast(
            this, 100, deleteIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.enchanting)
            .setContentTitle("联机工具正在运行")
            .setContentText(if (role == "host") "房主模式运行中." else "访客已连接")
            .setOngoing(true)                // 已有
            .setDeleteIntent(deletePending)  // ★ 新增：被“划掉”时回调

        // 通知按钮
        val exitIntent = Intent(this, ConnectionControlReceiver::class.java)
            .setAction("ACTION_EXIT")
        val exitPending = PendingIntent.getBroadcast(this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE)

        builder.addAction(Notification.Action.Builder(
            null, "退出房间", exitPending
        ).build())

        if (role == "host" && inviteCode != null) {
            val requestCode = SystemClock.uptimeMillis().toInt()
            val copyIntent = Intent(this, ConnectionControlReceiver::class.java)
                .setAction("ACTION_COPY_INVITE_CODE")
                .putExtra("invite_code", inviteCode)
                .setData("terracotta://copy_invite?sid=$requestCode".toUri())
            val copyPending = PendingIntent.getBroadcast(this, requestCode, copyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(
                null, "复制邀请码", copyPending
            ).build())
        } else {
            val requestCode = SystemClock.uptimeMillis().toInt()
            val copyIntent = Intent(this, ConnectionControlReceiver::class.java)
                .setAction("ACTION_COPY_SERVER")
                .putExtra("server", "127.0.0.1:" + forwardPort)
                .setData("terracotta://copy_server?sid=$requestCode".toUri())
            val copyPending = PendingIntent.getBroadcast(this, requestCode, copyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
