#include "mpv_network.h"
#include "mpv_wrapper.h"
#include <mpv/client.h>
#include <hilog/log.h>
#include <cstdio>
#include <chrono>
#include <cmath>

// Hilog domain/tag
#undef LOG_DOMAIN
#define LOG_DOMAIN 0xD003D00
#undef LOG_TAG
#define LOG_TAG "mpvnetwork"

// 引用全局 MPV 句柄
extern mpv_handle *global_mpv;

// 存储网速信息
static double current_speed_kbps = 0.0;
static char current_speed_str[32] = "0 KB/s";

napi_value GetNetworkSpeed(napi_env env, napi_callback_info info) {
    if (!global_mpv) {
        napi_value result;
        napi_create_object(env, &result);
        napi_value speed;
        napi_create_double(env, 0.0, &speed);
        napi_set_named_property(env, result, "speed", speed);
        napi_value speed_str;
        napi_create_string_utf8(env, "0 KB/s", 6, &speed_str);
        napi_set_named_property(env, result, "speedStr", speed_str);
        napi_value cache_size_kb;
        napi_create_double(env, 0.0, &cache_size_kb);
        napi_set_named_property(env, result, "cacheSizeKB", cache_size_kb);
        return result;
    }
    
    // 获取 cache-buffering-state 来检查缓冲状态
    int64_t cache_buffering = 0;
    mpv_get_property(global_mpv, "cache-buffering-state", MPV_FORMAT_INT64, &cache_buffering);
    
    // 获取 cache-speed 属性（字符串格式）
    char *speed_str_from_mpv = nullptr;
    int ret_speed = mpv_get_property(global_mpv, "cache-speed", MPV_FORMAT_STRING, &speed_str_from_mpv);
    
    double speed_from_property = 0.0;
    if (ret_speed == 0 && speed_str_from_mpv && strlen(speed_str_from_mpv) > 0) {
        speed_from_property = strtod(speed_str_from_mpv, nullptr);
        mpv_free(speed_str_from_mpv);
    }
    
    // cache-speed 返回的是 bytes/second，转换为 KB/s
    double speed_kbps = 0.0;
    if (speed_from_property > 0) {
        speed_kbps = speed_from_property / 1024.0;
    }
    
    // 更新全局状态
    current_speed_kbps = speed_kbps;
    if (speed_kbps < 1024.0) {
        snprintf(current_speed_str, sizeof(current_speed_str), "%.1f KB/s", speed_kbps);
    } else {
        snprintf(current_speed_str, sizeof(current_speed_str), "%.2f MB/s", speed_kbps / 1024.0);
    }
    
    // 创建返回值
    napi_value result_obj;
    napi_create_object(env, &result_obj);
    
    // speed (KB/s)
    napi_value speed;
    napi_create_double(env, speed_kbps, &speed);
    napi_set_named_property(env, result_obj, "speed", speed);
    
    // speedStr (格式化字符串)
    napi_value speed_str_value;
    napi_create_string_utf8(env, current_speed_str, strlen(current_speed_str), &speed_str_value);
    napi_set_named_property(env, result_obj, "speedStr", speed_str_value);
    
    // cacheSizeKB (当前缓冲状态 0-100%)
    napi_value cache_status;
    napi_create_double(env, cache_buffering, &cache_status);
    napi_set_named_property(env, result_obj, "cacheSizeKB", cache_status);
    
    return result_obj;
}
