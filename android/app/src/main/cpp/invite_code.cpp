//
// Created by hanji on 2025/7/27.
//

#include "invite_code.h"
#include <string>
#include <random>
#include <sstream>
#include <algorithm>

const std::string BASE34 = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

int lookup_base34(char c) {
    c = std::toupper(c);
    if (c == 'I') c = '1';
    if (c == 'O') c = '0';
    auto pos = BASE34.find(c);
    return (pos == std::string::npos) ? -1 : static_cast<int>(pos);
}

void bigint_add(std::vector<uint32_t>& value, uint32_t digit) {
    uint64_t carry = digit;
    for (size_t i = 0; i < value.size(); ++i) {
        uint64_t sum = static_cast<uint64_t>(value[i]) + carry;
        value[i] = static_cast<uint32_t>(sum);
        carry = sum >> 32;
        if (carry == 0) break;
    }
    if (carry > 0) value.push_back(static_cast<uint32_t>(carry));
}

void bigint_mul(std::vector<uint32_t>& value, uint32_t multiplier) {
    uint64_t carry = 0;
    for (size_t i = 0; i < value.size(); ++i) {
        uint64_t prod = static_cast<uint64_t>(value[i]) * multiplier + carry;
        value[i] = static_cast<uint32_t>(prod);
        carry = prod >> 32;
    }
    if (carry > 0) value.push_back(static_cast<uint32_t>(carry));
}

int checksum(const std::string& str) {
    int sum = 0;
    for (char c : str) sum += c;
    return sum % 34;
}

std::string insert_hyphens(const std::string& input) {
    std::string out;
    for (size_t i = 0; i < input.size(); ++i) {
        if (i > 0 && i % 5 == 0) out += '-';
        out += input[i];
    }
    return out;
}

std::string generate_invite_code(uint64_t room_id, uint16_t port) {
    return nullptr;
}

InviteParseResult parse_invite_code(const std::string& input) {
    InviteParseResult result;
    std::vector<int> digits;

    for (char c : input) {
        if (std::isalnum(c)) {
            int val = lookup_base34(c);
            if (val == -1) return result;
            digits.push_back(val);
        }
    }

    if (digits.size() != 25) return result;

    // 校验 checksum
    int checksum = 0;
    for (int i = 0; i < 24; ++i) checksum += digits[i];
    if ((checksum % 34) != digits[24]) return result;

    // 构建多精度 value（BigUint）
    std::vector<uint32_t> value(1, 0); // Little-endian

    for (int i = 0; i < 25; ++i) {
        bigint_mul(value, 34);
        bigint_add(value, digits[i]);
    }

    // 恢复为 uint64_t
    uint64_t total = 0;
    for (int i = 24; i >= 0; --i) {
        total = total * 34 + digits[i];
    }

    result.port = static_cast<uint16_t>(total & 0xFFFF);
    result.room_id = total >> 16;

    // 提取 name & secret
    result.name.clear();
    result.secret.clear();
    for (int i = 0; i < 15; ++i) result.name += BASE34[digits[i]];
    for (int i = 15; i < 25; ++i) result.secret += BASE34[digits[i]];

    result.valid = true;
    return result;
}
