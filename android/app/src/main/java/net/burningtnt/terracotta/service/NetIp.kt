package net.burningtnt.terracotta.service

import net.burningtnt.terracotta.core.RoomKind
import java.security.MessageDigest

private fun sha256FirstByte(s: String): Int {
    val md = MessageDigest.getInstance("SHA-256")
    val b = md.digest(s.toByteArray())
    return b[0].toInt() and 0xFF
}

/**
 * 为每个 Guest 生成稳定且低冲突的 IPv4：
 * - Terracotta 子网：10.144.144.0/24（避开 .1）
 * - PCL2CE   子网：10.114.51.0/24   （避开 .41）
 */
fun pickGuestIpV4(deviceUuid: String, roomKind: RoomKind, networkName: String, secret: String): String {
    val base = when (roomKind) {
        RoomKind.TERRACOTTA -> "10.144.144"
        RoomKind.PCL2CE     -> "10.114.51"
        else                -> "10.144.144"
    }
    val seed = "$deviceUuid|$roomKind|$networkName|$secret|$base"
    var host = (sha256FirstByte(seed) % 253) + 2   // 映射到 2..254
    if (roomKind == RoomKind.TERRACOTTA && host == 1) host = 2
    if (roomKind == RoomKind.PCL2CE && host == 41) host = if (host < 254) host + 1 else 42
    return "$base.$host"
}