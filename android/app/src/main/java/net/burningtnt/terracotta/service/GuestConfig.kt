package net.burningtnt.terracotta.service

import net.burningtnt.terracotta.core.RoomKind

data class GuestConfig(val networkName: String, val secret: String, val port: Int, val forwardPort: Int, val roomKind: RoomKind)
var pendingVpnGuestConfig: GuestConfig? = null
