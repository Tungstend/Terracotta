//
// Created by hanji on 2025/7/27.
//

#include "lan_scanner.h"

#include <thread>
#include <cstring>
#include <string>
#include <arpa/inet.h>
#include <unistd.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <iostream>
#include <android/log.h>

#define LOG_TAG "LANScanner"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

LANScanner::LANScanner() {}

LANScanner::~LANScanner() {
    stop();
}

static std::string get_base_ip(const std::string& ip) {
    auto last_dot = ip.rfind('.');
    return (last_dot == std::string::npos) ? "" : ip.substr(0, last_dot);
}

void LANScanner::start(const std::string& interface_ip, PortCallback cb) {
    running = true;
    std::string base_ip = get_base_ip(interface_ip);  // e.g., "192.168.1"

    int start_port = 10000;
    int end_port = 65535;

    scanner_thread = std::thread([this, base_ip, start_port, end_port, cb]() {
        while (running) {
            for (int i = 1; i <= 254 && running; ++i) {
                std::string target_ip = base_ip + "." + std::to_string(i);

                for (int port = start_port; port <= end_port && running; ++port) {
                    int sock = socket(AF_INET, SOCK_STREAM, 0);
                    if (sock < 0) continue;

                    sockaddr_in addr{};
                    addr.sin_family = AF_INET;
                    addr.sin_port = htons(port);
                    inet_pton(AF_INET, target_ip.c_str(), &addr.sin_addr);

                    timeval timeout = {1, 0};
                    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
                    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

                    int result = connect(sock, (sockaddr*)&addr, sizeof(addr));
                    close(sock);

                    if (result == 0) {
                        LOGD("🎯 找到可连接主机: %s:%d", target_ip.c_str(), port);
                        cb(port);
                    }
                }
            }

            // 每秒一次
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }
    });
}

void LANScanner::stop() {
    running = false;
    if (scanner_thread.joinable()) {
        scanner_thread.join();
    }
}
