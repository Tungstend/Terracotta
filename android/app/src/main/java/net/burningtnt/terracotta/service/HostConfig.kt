package net.burningtnt.terracotta.service

data class HostConfig(val networkName: String, val secret: String, val inviteCode: String)
var pendingVpnHostConfig: HostConfig? = null