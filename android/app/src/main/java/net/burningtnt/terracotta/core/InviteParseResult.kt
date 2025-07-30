package net.burningtnt.terracotta.core

data class InviteParseResult(
    val roomId: Long,
    val port: Int,
    val name: String,
    val secret: String
)
