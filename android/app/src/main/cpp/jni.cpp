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
        JNIEnv* env, jobject /*thiz*/, jstring code) {
    if (code == nullptr) return nullptr;

    // --- 1) 取入参字符串 ---
    const char* cstr = env->GetStringUTFChars(code, nullptr);
    if (!cstr) return nullptr; // OOM
    std::string input(cstr);
    env->ReleaseStringUTFChars(code, cstr);

    // --- 2) 解析邀请码（C++ 侧已实现 Terracotta / PCL2CE 兼容，并设置 room_kind）---
    InviteParseResult result = parse_invite_code(input);
    if (!result.valid) return nullptr;

    // --- 3) 准备 Java 类与枚举 ---
    jclass clsResult = env->FindClass("net/burningtnt/terracotta/core/InviteParseResult");
    if (!clsResult) return nullptr;

    jclass clsRoomKind = env->FindClass("net/burningtnt/terracotta/core/RoomKind");
    if (!clsRoomKind) return nullptr;

    // RoomKind.values()[index]
    jmethodID midValues = env->GetStaticMethodID(clsRoomKind, "values", "()[Lnet/burningtnt/terracotta/core/RoomKind;");
    if (!midValues) return nullptr;

    jobjectArray kindsArray = (jobjectArray)env->CallStaticObjectMethod(clsRoomKind, midValues);
    if (!kindsArray) return nullptr;

    // C++ -> Java enum 索引映射：
    // 假设你在 C++ 中定义了：
    // enum class RoomKind { TERRACOTTA = 0, PCL2CE = 1, INVALID = 2 };
    // 若你的枚举不同，请按需调整 idx 的取值。
    jint idx = 2; // 默认 INVALID
    switch (result.room_kind) {
        case RoomKind::TERRACOTTA: idx = 0; break;
        case RoomKind::PCL2CE:     idx = 1; break;
        default:                   idx = 2; break;
    }

    jobject roomKindObj = env->GetObjectArrayElement(kindsArray, idx);
    if (!roomKindObj) return nullptr;

    // --- 4) 构造 Kotlin data class（构造参数顺序务必与 InviteParseResult.kt 一致）---
    // data class InviteParseResult(
    //   val roomId: Long,
    //   val port: Int,
    //   val name: String,
    //   val secret: String,
    //   val roomKind: RoomKind
    // )
    jmethodID ctor = env->GetMethodID(
            clsResult,
            "<init>",
            "(JILjava/lang/String;Ljava/lang/String;Lnet/burningtnt/terracotta/core/RoomKind;)V");
    if (!ctor) return nullptr;

    jstring jname   = env->NewStringUTF(result.name.c_str());
    jstring jsecret = env->NewStringUTF(result.secret.c_str());
    if (!jname || !jsecret) {
        if (jname)   env->DeleteLocalRef(jname);
        if (jsecret) env->DeleteLocalRef(jsecret);
        return nullptr;
    }

    jobject jobj = env->NewObject(
            clsResult, ctor,
            (jlong)result.room_id,
            (jint) result.port,
            jname,
            jsecret,
            roomKindObj
    );

    // --- 5) 清理局部引用 ---
    env->DeleteLocalRef(jname);
    env->DeleteLocalRef(jsecret);
    env->DeleteLocalRef(roomKindObj);
    env->DeleteLocalRef(kindsArray);
    // clsResult / clsRoomKind 是类引用，JNI 会在栈帧退栈时清理；也可手动 DeleteLocalRef

    return jobj;
}

static std::unique_ptr<LANScanner> scanner;
static JavaVM* global_vm = nullptr;
static jobject global_callback = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_startLanScan(
        JNIEnv* env, jobject /* this */, jobject callback) {
    if (scanner) return;

    env->GetJavaVM(&global_vm);
    global_callback = env->NewGlobalRef(callback);

    scanner = std::make_unique<LANScanner>();
    scanner->start([](int port) {
        JNIEnv* env = nullptr;
        global_vm->AttachCurrentThread(&env, nullptr);
        jclass cb_class = env->GetObjectClass(global_callback);
        jmethodID onFound = env->GetMethodID(cb_class, "onPortFound", "(I)V");
        env->CallVoidMethod(global_callback, onFound, port);
    });
}

extern "C"
JNIEXPORT void JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_stopLanScan(JNIEnv*, jobject) {
    if (scanner) {
        scanner->stop();
        scanner.reset();
    }

    if (global_callback) {
        JNIEnv* env;
        global_vm->AttachCurrentThread(&env, nullptr);
        env->DeleteGlobalRef(global_callback);
        global_callback = nullptr;
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

extern int start_easytier_host(const std::string&, const std::string&, const std::string&);
extern int start_easytier_guest(const std::string&, const std::string&, int, int, int, const std::string&);

extern "C"
JNIEXPORT jint JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_startEasyTierHost(
        JNIEnv* env, jobject, jstring name, jstring key, jstring logDir) {
    const char* cname = env->GetStringUTFChars(name, nullptr);
    const char* ckey = env->GetStringUTFChars(key, nullptr);
    const char* clog = env->GetStringUTFChars(logDir, nullptr);
    int ret = start_easytier_host(cname, ckey, clog);
    env->ReleaseStringUTFChars(name, cname);
    env->ReleaseStringUTFChars(key, ckey);
    env->ReleaseStringUTFChars(logDir, clog);
    return ret;
}

extern "C"
JNIEXPORT jint JNICALL
Java_net_burningtnt_terracotta_core_NativeBridge_startEasyTierGuest(
        JNIEnv* env, jobject, jstring name, jstring key, jint local_port, jint remote_port, jobject roomKind, jstring logDir) {
    const char* cname = env->GetStringUTFChars(name, nullptr);
    const char* ckey = env->GetStringUTFChars(key, nullptr);
    const char* clog = env->GetStringUTFChars(logDir, nullptr);
    jclass clsRoomKind = env->FindClass("net/burningtnt/terracotta/core/RoomKind");
    jmethodID midOrdinal = env->GetMethodID(clsRoomKind, "ordinal", "()I");
    jint kindOrdinal = env->CallIntMethod(roomKind, midOrdinal);
    int ret = start_easytier_guest(cname, ckey, local_port, remote_port, kindOrdinal, clog);
    env->ReleaseStringUTFChars(name, cname);
    env->ReleaseStringUTFChars(key, ckey);
    env->ReleaseStringUTFChars(logDir, clog);
    return ret;
}