//
// Created by hanji on 2025/7/27.
//

#ifndef TERRACOTTA_INVITE_CODE_H
#define TERRACOTTA_INVITE_CODE_H

#include <string>
#include <cstdint>

enum class RoomKind {
    TERRACOTTA,  // 原生 Terracotta 邀请码
    PCL2CE,      // PCL2CE 邀请码
    INVALID      // 无效码
};

struct InviteParseResult {
    uint64_t room_id;
    uint16_t port;
    std::string name;   // Terracotta: 15位 base34；PCL2CE: 十进制的前8位
    std::string secret; // Terracotta: 10位 base34；PCL2CE: 十进制的第9~10位
    RoomKind room_kind = RoomKind::INVALID;
    bool valid = false;
};


std::string generate_invite_code(uint64_t room_id, uint16_t port);
InviteParseResult parse_invite_code(const std::string& input);

#endif //TERRACOTTA_INVITE_CODE_H
