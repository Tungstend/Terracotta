//
// Created by hanji on 2025/7/27.
//

#ifndef TERRACOTTA_LAN_SCANNER_H
#define TERRACOTTA_LAN_SCANNER_H

#include <string>
#include <thread>
#include <atomic>
#include <functional>

class LANScanner {
public:
    using PortCallback = std::function<void(int)>;

    LANScanner();
    ~LANScanner();

    void start(const std::string& interface_ip, PortCallback cb);
    void stop();

private:
    std::thread scanner_thread;
    std::atomic<bool> running = false;
};

#endif //TERRACOTTA_LAN_SCANNER_H
