package net.burningtnt.terracotta.core

enum class RoomKind { TERRACOTTA, PCL2CE, INVALID }

data class InviteParseResult(
    val roomId: Long,
    val port: Int,
    val name: String,      // Terracotta: 15位base34；PCL2CE: 十进制前8位
    val secret: String,    // Terracotta: 10位base34；PCL2CE: 十进制第9~10位
    val roomKind: RoomKind // 新增
)
