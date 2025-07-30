package net.burningtnt.terracotta.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ConnectionControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            "ACTION_EXIT" -> {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            "ACTION_COPY_INVITE_CODE" -> {
                val code = intent.getStringExtra("invite_code") ?: return
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("邀请码", code))
                Toast.makeText(context, "已复制邀请码", Toast.LENGTH_SHORT).show()
            }
            "ACTION_COPY_SERVER" -> {
                val server = intent.getStringExtra("server") ?: return
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("服务器地址", server))
                Toast.makeText(context, "已复制服务器地址", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
