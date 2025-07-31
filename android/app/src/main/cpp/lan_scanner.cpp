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

void LANScanner::start(PortCallback cb) {
    running = true;
    scanner_thread = std::thread([this, cb]() {
        int sock = socket(AF_INET, SOCK_DGRAM, 0);
        if (sock < 0) {
            LOGE("创建 UDP socket 失败");
            return;
        }

        // 允许多个程序绑定同一端口（Android 重要）
        int reuse = 1;
        setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (char*)&reuse, sizeof(reuse));

        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(4445);
        addr.sin_addr.s_addr = htonl(INADDR_ANY);

        if (bind(sock, (sockaddr*)&addr, sizeof(addr)) < 0) {
            LOGE("bind失败");
            close(sock);
            return;
        }

        ip_mreq mreq{};
        mreq.imr_multiaddr.s_addr = inet_addr("224.0.2.60");
        mreq.imr_interface.s_addr = htonl(INADDR_ANY);
        if (setsockopt(sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq)) < 0) {
            LOGE("加入多播组失败");
            close(sock);
            return;
        }

        char buf[2048];
        while (running) {
            sockaddr_in src{};
            socklen_t len = sizeof(src);
            int n = recvfrom(sock, buf, sizeof(buf) - 1, 0, (sockaddr*)&src, &len);
            if (n > 0) {
                buf[n] = '\0';
                std::string msg(buf);
                LOGD("🎮 收到广播: %s", msg.c_str());

                auto ad_start = msg.find("[AD]");
                auto ad_end = msg.find("[/AD]");
                if (ad_start != std::string::npos && ad_end != std::string::npos) {
                    std::string port_str = msg.substr(ad_start + 4, ad_end - (ad_start + 4));
                    int port = std::stoi(port_str);
                    LOGD("✅ 捕获端口: %d", port);
                    cb(port);
                    break;  // 监听到一次就退出（行为与陶瓦一致）
                }
            }
        }

        close(sock);
    });
}

void LANScanner::stop() {
    running = false;
    if (scanner_thread.joinable()) {
        if (std::this_thread::get_id() == scanner_thread_id) {
            // 🚫 不能从 scanner_thread 内部调用 join（否则死锁）
            LOGD("跳过 join()，避免死锁");
            return;
        }
        scanner_thread.join();
    }
}
