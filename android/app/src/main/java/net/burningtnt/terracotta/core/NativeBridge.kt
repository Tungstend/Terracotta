package net.burningtnt.terracotta.core

object NativeBridge {

    init {
        System.loadLibrary("terracotta")
    }

    external fun generateInviteCode(port: Int): String
    external fun parseInviteCode(code: String): InviteParseResult?
    external fun startLanScan(ip: String, callback: LanScanCallback)
    external fun stopLanScan()
    external fun startFakeServer(motd: String, listenPort: Int)
    external fun stopFakeServer()
    external fun startEasyTierHost(name: String, key: String, port: Int, logDir: String): Int
    external fun startEasyTierGuest(name: String, key: String, localPort: Int, remotePort: Int, logDir: String): Int

}