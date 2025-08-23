package net.burningtnt.terracotta.service

import android.content.Context
import java.util.UUID

private const val PREFS = "terracotta_prefs"
private const val KEY_DEVICE_UUID = "device_uuid"

fun getDeviceUUID(ctx: Context): String {
    val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val existing = sp.getString(KEY_DEVICE_UUID, null)
    if (existing != null) return existing
    val uuid = UUID.randomUUID().toString()
    sp.edit().putString(KEY_DEVICE_UUID, uuid).apply()
    return uuid
}