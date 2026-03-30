#include "mpv_playback.h"
#include "mpv_wrapper.h"
#include <mpv/client.h>
#include <hilog/log.h>
#include <cstdio>
#include <cstring>
#include <atomic>

#undef LOG_DOMAIN
#define LOG_DOMAIN 0xD003D00
#undef LOG_TAG
#define LOG_TAG "mpvplayback"

double cached_duration = 0.0;
bool duration_available = false;
std::atomic<bool> cached_pause{false};

extern mpv_handle *global_mpv;

void reset_all_cached_properties() {
    cached_duration = 0.0;
    duration_available = false;
    cached_pause.store(false, std::memory_order_relaxed);
}

napi_value Seek(napi_env env, napi_callback_info info) {
    size_t argc = 3;
    napi_value args[3];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    
    if (argc < 2 || !global_mpv) {
        return nullptr;
    }
    
    double position = 0;
    napi_get_value_double(env, args[1], &position);
    
    // Optional: exact flag (default false for faster seeking)
    bool exact = false;
    if (argc >= 3) {
        napi_get_value_bool(env, args[2], &exact);
    }
    
    // Use mpv command for seeking
    // Format: ["seek", "<position>", "absolute", "exact"/"keyframes"]
    const char *seek_cmd[5];
    char pos_str[32];
    snprintf(pos_str, sizeof(pos_str), "%.3f", position);
    
    seek_cmd[0] = "seek";
    seek_cmd[1] = pos_str;
    seek_cmd[2] = "absolute";
    seek_cmd[3] = exact ? "exact" : "keyframes";
    seek_cmd[4] = nullptr;
    
    mpv_command(global_mpv, seek_cmd);
    
    return nullptr;
}

napi_value Play(napi_env env, napi_callback_info info) {
    // 检查全局mpv实例是否存在
    if (!global_mpv) {
        napi_throw_error(env, nullptr, "MPV instance not initialized");
        return nullptr;
    }
    
    // 设置暂停属性为false（播放）
    int flag = 0;
    mpv_set_property(global_mpv, "pause", MPV_FORMAT_FLAG, &flag);
    
    return nullptr;
}

napi_value Pause(napi_env env, napi_callback_info info) {
    // 检查全局mpv实例是否存在
    if (!global_mpv) {
        napi_throw_error(env, nullptr, "MPV instance not initialized");
        return nullptr;
    }
    
    // 设置暂停属性为true（暂停）
    int flag = 1;
    mpv_set_property(global_mpv, "pause", MPV_FORMAT_FLAG, &flag);
    
    return nullptr;
}

napi_value SetSpeed(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    
    if (argc < 2 || !global_mpv) {
        return nullptr;
    }
    
    double speed = 1.0;
    napi_get_value_double(env, args[1], &speed);
    
    // Set the "speed" property in MPV
    mpv_set_property(global_mpv, "speed", MPV_FORMAT_DOUBLE, &speed);
    
    return nullptr;
}

napi_value GetCurrentPosition(napi_env env, napi_callback_info info) {
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetCurrentPosition] ERROR: mpv not created");
        napi_value result;
        napi_create_double(env, 0.0, &result);
        return result;
    }
    
    double time_pos = 0.0;
    int ret = mpv_get_property(global_mpv, "time-pos", MPV_FORMAT_DOUBLE, &time_pos);
    
    if (ret < 0) {
        OH_LOG_WARN(LOG_APP, "[GetCurrentPosition] Failed to get time-pos: %{public}s", mpv_error_string(ret));
        time_pos = 0.0;
    } else {
        OH_LOG_INFO(LOG_APP, "[GetCurrentPosition] time-pos: %{public}.3f seconds", time_pos);
    }
    
    // Convert seconds to milliseconds
    double time_ms = time_pos * 1000.0;
    OH_LOG_INFO(LOG_APP, "[GetCurrentPosition] Returning: %{public}.0f ms", time_ms);
    
    napi_value result;
    napi_create_double(env, time_ms, &result);
    return result;
}

napi_value GetDuration(napi_env env, napi_callback_info info) {
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetDuration] ERROR: mpv not created");
        napi_value result;
        napi_create_double(env, 0.0, &result);
        return result;
    }
    
    double duration = 0.0;
    
    // 优先使用事件机制缓存的 duration
    if (duration_available && cached_duration > 0) {
        duration = cached_duration;
        // 减少日志输出频率
        static int log_counter = 0;
        bool should_log = ((log_counter % 100) == 0);
        log_counter++;  // 副作用单独一行
        
        if (should_log) {
            OH_LOG_INFO(LOG_APP, "[GetDuration] Using cached duration: %{public}.3f seconds", duration);
        }
    } else {
        // 如果缓存不可用，主动获取并更新缓存（使用 duration/full 获取完整时长）
        int ret = mpv_get_property(global_mpv, "duration/full", MPV_FORMAT_DOUBLE, &duration);
        
        if (ret < 0) {
            // MPV_ERROR_PROPERTY_UNAVAILABLE (-12) 表示属性还不可用（视频还在加载）
            if (ret == MPV_ERROR_PROPERTY_UNAVAILABLE) {
                OH_LOG_INFO(LOG_APP, "[GetDuration] Duration not yet available (video still loading)");
            } else {
                OH_LOG_WARN(LOG_APP, "[GetDuration] Failed to get duration: %{public}s (code: %{public}d)", mpv_error_string(ret), ret);
            }
            duration = 0.0;
        } else if (duration <= 0) {
            OH_LOG_WARN(LOG_APP, "[GetDuration] Got invalid duration: %{public}.3f seconds", duration);
            duration = 0.0;
        } else {
            // 成功获取，立即更新缓存
            if (!duration_available || duration != cached_duration) {
                OH_LOG_INFO(LOG_APP, "[GetDuration] ✓ Got and cached duration: %{public}.3f seconds (%{public}.0f ms)", 
                           duration, duration * 1000.0);
                cached_duration = duration;
                duration_available = true;
            }
        }
    }
    
    // Convert seconds to milliseconds
    double duration_ms = duration * 1000.0;
    
    napi_value result;
    napi_create_double(env, duration_ms, &result);
    return result;
}

napi_value GetCacheDuration(napi_env env, napi_callback_info info) {
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetCacheDuration] ERROR: mpv not created");
        napi_value result;
        napi_create_double(env, 0.0, &result);
        return result;
    }
    
    // Get demuxer cache duration (in seconds)
    double cache_duration = 0.0;
    int ret = mpv_get_property(global_mpv, "demuxer-cache-duration", MPV_FORMAT_DOUBLE, &cache_duration);
    
    if (ret < 0) {
        if (ret == MPV_ERROR_PROPERTY_UNAVAILABLE) {
            OH_LOG_DEBUG(LOG_APP, "[GetCacheDuration] Cache duration not yet available");
        } else {
            OH_LOG_WARN(LOG_APP, "[GetCacheDuration] Failed to get cache duration: %{public}s", mpv_error_string(ret));
        }
        cache_duration = 0.0;
    }
    
    // Convert seconds to milliseconds
    double cache_duration_ms = cache_duration * 1000.0;
    
    napi_value result;
    napi_create_double(env, cache_duration_ms, &result);
    return result;
}
