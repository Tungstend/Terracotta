package net.burningtnt.terracotta.core

import android.os.ParcelFileDescriptor

object NativeBridge {

    init {
        System.loadLibrary("terracotta")
    }

    external fun generateInviteCode(port: Int): String
    external fun parseInviteCode(code: String): InviteParseResult?
    external fun startLanScan(callback: LanScanCallback)
    external fun stopLanScan()
    external fun startFakeServer(motd: String, listenPort: Int)
    external fun stopFakeServer()
    external fun setTunFd(instanceName: String, tunFd: ParcelFileDescriptor): Int
    external fun retainNetworkInstance(names: Array<String>): Int
    external fun startEasyTierHost(name: String, key: String, logDir: String): Int
    external fun startEasyTierGuest(name: String, key: String, localPort: Int, remotePort: Int, logDir: String): Int

}