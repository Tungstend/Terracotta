//
// Created by hanji on 2025/7/27.
//

#include "easytier.h"
#include <string>
#include <sstream>
#include <thread>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <android/log.h>

#include <ifaddrs.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "EasyTier", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "EasyTier", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "EasyTier", __VA_ARGS__)

void print_local_ips() {
    struct ifaddrs *ifaddr, *ifa;
    char addr[INET_ADDRSTRLEN];

    if (getifaddrs(&ifaddr) == -1) {
        __android_log_print(ANDROID_LOG_ERROR, "Net", "getifaddrs failed");
        return;
    }

    for (ifa = ifaddr; ifa != nullptr; ifa = ifa->ifa_next) {
        if (ifa->ifa_addr == nullptr) continue;

        if (ifa->ifa_addr->sa_family == AF_INET) {
            void* in_addr = &((struct sockaddr_in*)ifa->ifa_addr)->sin_addr;
            inet_ntop(AF_INET, in_addr, addr, INET_ADDRSTRLEN);
            __android_log_print(ANDROID_LOG_INFO, "Net", "Interface: %s, IP: %s", ifa->ifa_name, addr);
        }
    }

    freeifaddrs(ifaddr);
}

void print_last_error() {
    const char* err_msg = nullptr;
    get_error_msg(&err_msg);
    if (err_msg) {
        __android_log_print(ANDROID_LOG_ERROR, "EasyTier", "Error: %s", err_msg);
        // 可选释放：如果 Rust 端未保留指针，也可省略
        free((void*)err_msg);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "EasyTier", "Unknown error (no message)");
    }
}

void log_key_value(const char* key, const char* value) {
    const int chunkSize = 1000;
    if (strlen(value) <= chunkSize) {
        LOGI("  %s = %s", key, value);
    } else {
        LOGI("  %s =", key);
        int len = strlen(value);
        for (int j = 0; j < len; j += chunkSize) {
            char buffer[chunkSize + 1] = {0};
            strncpy(buffer, value + j, chunkSize);
            LOGI("    >> %s", buffer);
        }
    }
}

// 打印网络信息
void print_network_infos() {
    const size_t MAX_INFO = 64;
    KeyValuePair infos[MAX_INFO];

    int count = collect_network_infos(infos, MAX_INFO);
    if (count < 0) {
        LOGE("❌ collect_network_infos failed");
        return;
    }

    LOGI("✅ Network status update:");
    for (int i = 0; i < count; ++i) {
        log_key_value(infos[i].key, infos[i].value);
        print_last_error();
    }
}

// 循环监控线程
void monitor_network_thread() {
    while (true) {
        print_network_infos();
        std::this_thread::sleep_for(std::chrono::seconds(10));
    }
}

// 🟢 房主模式
int start_easytier_host(const std::string& network_name, const std::string& secret, int port, const std::string& log_dir) {
    std::ostringstream oss;
    oss << R"({
        "instance_name": "host-)" << std::chrono::system_clock::now().time_since_epoch().count() << R"(",
        "virtual_ipv4": "10.144.144.1",
        "network_name": ")" << network_name << R"(",
        "network_secret": ")" << secret << R"(",
        "mode": "host",
        "tcp_keepalive": true,
        "compress": true,
        "relay_servers": ["public.easytier.top:11010"]
    })";

    std::string json = oss.str();
    return run_network_instance(json.c_str());
}

// 🔵 访客模式
int start_easytier_guest(const std::string& network_name, const std::string& secret,
                         int local_port, int remote_port, const std::string& log_dir) {
    bool ipv6_only = false;
    {
        int fd = socket(AF_INET6, SOCK_DGRAM, 0);
        if (fd >= 0) {
            int val = 0;
            socklen_t len = sizeof(val);
            if (getsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, &val, &len) == 0) {
                ipv6_only = (val == 1);
            }
            close(fd);
        }
    }

    std::ostringstream oss;
    oss << "instance_name = \"Terracotta-Guest\"" << "\n\n";

    oss << "[file_logger]\n";
    oss << "level = \"debug\"\n";
    oss << "file = \"guest.log\"\n";
    oss << "dir = \"" << log_dir << "\"\n\n";

    oss << "[console_logger]\n";
    oss << "level = \"debug\"\n\n";

    oss << "[network_identity]\n";
    oss << "network_name = \"" << network_name << "\"\n";
    oss << "network_secret = \"" << secret << "\"\n\n";

    // 统一放入 [flags]
    oss << "[flags]\n";
    //oss << "no_tun = true\n";
    oss << "compression = \"zstd\"\n";
    oss << "multi_thread = true\n";
    oss << "latency_first = true\n";
    oss << "enable_kcp_proxy = true\n\n";

    // 端口转发规则（IPv6）
    //oss << "[[port_forward]]\n";
    //oss << "proto = \"tcp\"\n";
    //oss << "bind_addr = \"[::]:" << local_port << "\"\n";
    //oss << "dst_addr = \"10.144.144.1:" << remote_port << "\"\n\n";

    //if (!ipv6_only) {
    //    oss << "[[port_forward]]\n";
    //    oss << "proto = \"tcp\"\n";
    //    oss << "bind_addr = \"0.0.0.0:" << local_port << "\"\n";
    //    oss << "dst_addr = \"10.144.144.1:" << remote_port << "\"\n\n";
    //}

    // 写 relay servers 为 [[peers]]
    const char* peers[] = {
            "tcp://public.easytier.top:11010",
            "tcp://ah.nkbpal.cn:11010",
            "tcp://turn.hb.629957.xyz:11010",
            "tcp://turn.js.629957.xyz:11012",
            "tcp://sh.993555.xyz:11010",
            "tcp://turn.bj.629957.xyz:11010",
            "tcp://et.sh.suhoan.cn:11010",
            "tcp://et-hk.clickor.click:11010",
            "tcp://et.01130328.xyz:11010",
            "tcp://et.gbc.moe:11011"
    };

    for (const auto& uri : peers) {
        oss << "[[peer]]\n";
        oss << "uri = \"" << uri << "\"\n\n";
    }

    // 添加结尾标记以便排查配置截断问题
    oss << "\n# === END OF CONFIG ===\n";

    std::string toml_config = oss.str();
    std::istringstream iss(toml_config);
    std::string line;
    while (std::getline(iss, line)) {
        __android_log_print(ANDROID_LOG_INFO, "EasyTier TOML", "%s", line.c_str());
    }

    // 解析配置
    if (parse_config(toml_config.c_str()) != 0)
        print_last_error();

    int ret = run_network_instance(toml_config.c_str());
    print_local_ips();
    if (ret != 0) {
        print_last_error();
    }

    /*constexpr size_t MAX_INFOS = 10;
    KeyValuePair infos[MAX_INFOS];

    int count = collect_network_infos(infos, MAX_INFOS);
    if (count <= 0)
        LOGW("⚠️ 无法获取 EasyTier 网络状态");

    for (int i = 0; i < count; ++i) {
        if (infos[i].key && infos[i].value) {
            LOGI("📡 %s -> %s", infos[i].key, infos[i].value);
            free_string(infos[i].key);
            free_string(infos[i].value);
        }
    }*/

    std::thread monitor(monitor_network_thread);
    monitor.detach();  // 后台运行

    return ret;
}
