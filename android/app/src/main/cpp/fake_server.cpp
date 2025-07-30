//
// Created by hanji on 2025/7/27.
//

// fake_server.cpp
#include "fake_server.h"
#include <thread>
#include <chrono>
#include <cstring>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "FakeServer", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "FakeServer", __VA_ARGS__)

FakeServer::FakeServer(int port, const std::string& motd)
        : listen_port(port), motd(motd), running(true) {

    broadcast_thread = std::thread([=]() {
        int sock = socket(AF_INET, SOCK_DGRAM, 0);
        if (sock < 0) {
            LOGE("❌ 创建广播socket失败");
            return;
        }

        int ttl = 4;
        int loop = 1;
        setsockopt(sock, IPPROTO_IP, IP_MULTICAST_TTL, &ttl, sizeof(ttl));
        setsockopt(sock, IPPROTO_IP, IP_MULTICAST_LOOP, &loop, sizeof(loop));

        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(4445);
        addr.sin_addr.s_addr = inet_addr("224.0.2.60");

        char buffer[512];
        snprintf(buffer, sizeof(buffer), "[MOTD]%s[/MOTD][AD]%d[/AD]", motd.c_str(), port);

        LOGI("📡 启动广播线程，MOTD=%s，端口=%d", motd.c_str(), port);

        while (running) {
            sendto(sock, buffer, strlen(buffer), 0, (sockaddr*)&addr, sizeof(addr));
            std::this_thread::sleep_for(std::chrono::milliseconds(1500));
        }

        close(sock);
    });
}

// ✅ 不再需要 listen_thread 和转发逻辑

FakeServer::~FakeServer() {
    running = false;
    if (broadcast_thread.joinable()) broadcast_thread.join();
}

void FakeServer::stop() {
    running = false;
}
