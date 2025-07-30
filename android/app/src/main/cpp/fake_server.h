//
// Created by hanji on 2025/7/27.
//

#ifndef TERRACOTTA_FAKE_SERVER_H
#define TERRACOTTA_FAKE_SERVER_H

#pragma once

#include <string>
#include <thread>
#include <atomic>

class FakeServer {
public:
    /**
     * 构造 FakeServer 并立即启动广播与监听线程
     * @param port Minecraft 实际监听端口
     * @param motd Minecraft 显示的房间名称
     */
    FakeServer(int port, const std::string& motd);

    /**
     * 析构函数，自动停止所有线程
     */
    ~FakeServer();

    /**
     * 手动停止 FakeServer（也可自动通过析构触发）
     */
    void stop();

private:
    int listen_port;
    std::string motd;
    std::atomic<bool> running;

    std::thread broadcast_thread;
    std::thread listen_thread;
};

#endif //TERRACOTTA_FAKE_SERVER_H
