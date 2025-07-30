package net.burningtnt.terracotta.service

data class GuestConfig(val networkName: String, val secret: String, val port: Int, val forwardPort: Int)
var pendingVpnGuestConfig: GuestConfig? = null
const val REQUEST_CODE_VPN = 1000
