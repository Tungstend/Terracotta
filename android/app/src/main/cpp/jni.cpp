//
// Created by hanji on 2025/7/27.
//

#include <jni.h>
#include <string>
#include <memory>
#include <random>
#include <android/log.h>

#include "invite_code.h"
#include "lan_scanner.h"
#include "fake_server.h"
#include "easytier.h"

extern "C"
JNIEXPORT jstring JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_generateInviteCode(
        JNIEnv* env, jobject /* this */, jint port) {
    std::random_device rd;
    uint64_t room_id = ((uint64_t)rd() << 32) | rd();
    std::string code = generate_invite_code(room_id, static_cast<uint16_t>(port));
    return env->NewStringUTF(code.c_str());
}

extern "C"
JNIEXPORT jobject JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_parseInviteCode(
        JNIEnv* env, jobject, jstring code) {
    const char* cstr = env->GetStringUTFChars(code, nullptr);
    std::string input(cstr);
    env->ReleaseStringUTFChars(code, cstr);

    InviteParseResult result = parse_invite_code(input);
    if (!result.valid) return nullptr;

    // 获取 Java 类
    jclass resultCls = env->FindClass("net/burningtnt/terracotta/core/InviteParseResult");
    if (resultCls == nullptr) return nullptr;

    // 构造函数签名: (JILjava/lang/String;Ljava/lang/String;)V
    jmethodID ctor = env->GetMethodID(resultCls, "<init>", "(JILjava/lang/String;Ljava/lang/String;)V");
    if (ctor == nullptr) return nullptr;

    // 构造 Java 字符串
    jstring jname = env->NewStringUTF(result.name.c_str());
    jstring jsecret = env->NewStringUTF(result.secret.c_str());

    // 创建 Java 对象
    jobject jobj = env->NewObject(resultCls, ctor, (jlong)result.room_id, (jint)result.port, jname, jsecret);

    // 删除局部引用（可选优化）
    env->DeleteLocalRef(jname);
    env->DeleteLocalRef(jsecret);

    return jobj;
}

static std::unique_ptr<LANScanner> scanner;
static JavaVM* global_vm = nullptr;
static jobject global_callback_obj = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_startLanScan(
        JNIEnv* env, jobject thiz, jstring ip, jobject callback) {
    if (scanner) return;

    const char* ip_cstr = env->GetStringUTFChars(ip, nullptr);
    std::string ip_cpp(ip_cstr);
    env->ReleaseStringUTFChars(ip, ip_cstr);

    global_vm = nullptr;
    env->GetJavaVM(&global_vm);
    global_callback_obj = env->NewGlobalRef(callback);

    scanner = std::make_unique<LANScanner>();
    scanner->start(ip_cpp, [](int port) {
        // 回调 Kotlin：callback.onPortFound(int)
        JNIEnv* env = nullptr;
        global_vm->AttachCurrentThread(&env, nullptr);
        jclass cls = env->GetObjectClass(global_callback_obj);
        jmethodID method = env->GetMethodID(cls, "onPortFound", "(I)V");
        env->CallVoidMethod(global_callback_obj, method, port);
    });
}

extern "C"
JNIEXPORT void JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_stopLanScan(
        JNIEnv*, jobject) {
    if (scanner) {
        scanner->stop();
        scanner.reset();
    }
}

static std::unique_ptr<FakeServer> server;

extern "C"
JNIEXPORT void JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_startFakeServer(
        JNIEnv* env, jobject, jstring motd_, jint listen_port) {
    if (server)
        return;

    const char* motd_cstr = env->GetStringUTFChars(motd_, nullptr);
    std::string motd(motd_cstr);
    env->ReleaseStringUTFChars(motd_, motd_cstr);

    server = std::make_unique<FakeServer>(listen_port, motd);
}

extern "C"
JNIEXPORT void JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_stopFakeServer(JNIEnv*, jobject) {
    if (server) {
        server->stop();
        server.reset();
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_setTunFd(
        JNIEnv* env,
        jclass clazz,
        jstring instanceName_,
        jobject tunParcelFileDescriptor
) {
    // 获取实例名字符串
    const char* instanceName = env->GetStringUTFChars(instanceName_, nullptr);

    // 获取 tun fd（从 ParcelFileDescriptor）
    jclass pfdClass = env->GetObjectClass(tunParcelFileDescriptor);
    jmethodID getFdMethod = env->GetMethodID(pfdClass, "getFd", "()I");
    if (getFdMethod == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "EasyTier", "无法获取 getFd 方法");
        env->ReleaseStringUTFChars(instanceName_, instanceName);
        return -2;
    }
    jint fd = env->CallIntMethod(tunParcelFileDescriptor, getFdMethod);

    // 调用 Rust 的 set_tun_fd
    int result = set_tun_fd(instanceName, fd);
    env->ReleaseStringUTFChars(instanceName_, instanceName);

    if (result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "EasyTier", "set_tun_fd(%s, fd=%d) 失败", instanceName, fd);
    } else {
        __android_log_print(ANDROID_LOG_INFO, "EasyTier", "✅ set_tun_fd 成功：%s (fd=%d)", instanceName, fd);
    }

    return result;
}

extern "C"
JNIEXPORT jint JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_retainNetworkInstance(
        JNIEnv* env,
        jclass clazz,
        jobjectArray names
) {
    jsize len = env->GetArrayLength(names);
    std::vector<const char*> cStrings;
    for (jsize i = 0; i < len; ++i) {
        jstring jstr = (jstring) env->GetObjectArrayElement(names, i);
        const char* cstr = env->GetStringUTFChars(jstr, nullptr);
        cStrings.push_back(cstr);
    }

    int result = retain_network_instance(cStrings.data(), cStrings.size());

    // 注意释放
    for (jsize i = 0; i < len; ++i) {
        jstring jstr = (jstring) env->GetObjectArrayElement(names, i);
        env->ReleaseStringUTFChars(jstr, cStrings[i]);
    }

    return result;
}

extern int start_easytier_host(const std::string&, const std::string&, int, const std::string&);
extern int start_easytier_guest(const std::string&, const std::string&, int, int, const std::string&);

extern "C"
JNIEXPORT jint JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_startEasyTierHost(
        JNIEnv* env, jobject, jstring name, jstring key, jint port, jstring logDir) {
    const char* cname = env->GetStringUTFChars(name, nullptr);
    const char* ckey = env->GetStringUTFChars(key, nullptr);
    const char* clog = env->GetStringUTFChars(logDir, nullptr);
    int ret = start_easytier_host(cname, ckey, port, clog);
    env->ReleaseStringUTFChars(name, cname);
    env->ReleaseStringUTFChars(key, ckey);
    env->ReleaseStringUTFChars(logDir, clog);
    return ret;
}

extern "C"
JNIEXPORT jint JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_startEasyTierGuest(
        JNIEnv* env, jobject, jstring name, jstring key, jint local_port, jint remote_port, jstring logDir) {
    const char* cname = env->GetStringUTFChars(name, nullptr);
    const char* ckey = env->GetStringUTFChars(key, nullptr);
    const char* clog = env->GetStringUTFChars(logDir, nullptr);
    int ret = start_easytier_guest(cname, ckey, local_port, remote_port, clog);
    env->ReleaseStringUTFChars(name, cname);
    env->ReleaseStringUTFChars(key, ckey);
    env->ReleaseStringUTFChars(logDir, clog);
    return ret;
}