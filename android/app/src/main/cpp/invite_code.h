//
// Created by hanji on 2025/7/27.
//

#ifndef TERRACOTTA_INVITE_CODE_H
#define TERRACOTTA_INVITE_CODE_H

#include <string>
#include <cstdint>

struct InviteParseResult {
    uint64_t room_id;
    uint16_t port;
    std::string name;   // 15位
    std::string secret; // 10位
    bool valid = false;
};

std::string generate_invite_code(uint64_t room_id, uint16_t port);
InviteParseResult parse_invite_code(const std::string& input);

#endif //TERRACOTTA_INVITE_CODE_H
