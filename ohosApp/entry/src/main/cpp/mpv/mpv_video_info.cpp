#include "mpv_video_info.h"
#include <mpv/client.h>
#include <hilog/log.h>
#include <cstring>

#undef LOG_DOMAIN
#define LOG_DOMAIN 0xD003D00
#undef LOG_TAG
#define LOG_TAG "mpvnative"

// 外部全局 MPV 句柄（在 mpv_wrapper.cpp 中定义）
extern mpv_handle *global_mpv;

napi_value GetHardwareDecoder(napi_env env, napi_callback_info info) {
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetHardwareDecoder] ERROR: mpv not created");
        napi_value result;
        napi_create_string_utf8(env, "unknown", NAPI_AUTO_LENGTH, &result);
        return result;
    }
    
    // First check what hwdec is configured
    char *hwdec_requested = mpv_get_property_string(global_mpv, "hwdec");
    if (hwdec_requested) {
        OH_LOG_INFO(LOG_APP, "[GetHardwareDecoder] Configured hwdec: %{public}s", hwdec_requested);
        mpv_free(hwdec_requested);
    }
    
    // Get the actual hardware decoder being used
    char *hwdec_current = mpv_get_property_string(global_mpv, "hwdec-current");
    if (hwdec_current) {
        OH_LOG_INFO(LOG_APP, "[GetHardwareDecoder] Current hwdec: %{public}s", hwdec_current);
        
        napi_value result;
        napi_create_string_utf8(env, hwdec_current, NAPI_AUTO_LENGTH, &result);
        
        // Log whether using hardware or software
        if (strcmp(hwdec_current, "no") == 0) {
            OH_LOG_WARN(LOG_APP, "[GetHardwareDecoder] Using SOFTWARE decoding");
        } else {
            OH_LOG_INFO(LOG_APP, "[GetHardwareDecoder] Using HARDWARE decoding: %{public}s", hwdec_current);
        }
        
        mpv_free(hwdec_current);
        return result;
    } else {
        OH_LOG_WARN(LOG_APP, "[GetHardwareDecoder] hwdec-current property not available");
        OH_LOG_INFO(LOG_APP, "[GetHardwareDecoder] This usually means video decoding hasn't started yet");
        
        // Try to get more info about why it's not available
        int64_t width = 0;
        if (mpv_get_property(global_mpv, "width", MPV_FORMAT_INT64, &width) == 0 && width > 0) {
            OH_LOG_INFO(LOG_APP, "[GetHardwareDecoder] Video has dimensions, decoder should be active");
        } else {
            OH_LOG_INFO(LOG_APP, "[GetHardwareDecoder] Video dimensions not available, file may not be fully loaded");
        }
        
        napi_value result;
        napi_create_string_utf8(env, "unknown", NAPI_AUTO_LENGTH, &result);
        return result;
    }
}

napi_value GetVideoWidth(napi_env env, napi_callback_info info) {
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetVideoWidth] ERROR: mpv not created");
        napi_value result;
        napi_create_int32(env, 0, &result);
        return result;
    }
    
    int64_t width = 0;
    int ret = mpv_get_property(global_mpv, "width", MPV_FORMAT_INT64, &width);
    
    if (ret == 0 && width > 0) {
        OH_LOG_INFO(LOG_APP, "[GetVideoWidth] Video width: %{public}lld", (long long)width);
    } else {
        OH_LOG_WARN(LOG_APP, "[GetVideoWidth] Video width not available (ret=%{public}d)", ret);
    }
    
    napi_value result;
    napi_create_int32(env, static_cast<int32_t>(width), &result);
    return result;
}

napi_value GetVideoHeight(napi_env env, napi_callback_info info) {
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetVideoHeight] ERROR: mpv not created");
        napi_value result;
        napi_create_int32(env, 0, &result);
        return result;
    }
    
    int64_t height = 0;
    int ret = mpv_get_property(global_mpv, "height", MPV_FORMAT_INT64, &height);
    
    if (ret == 0 && height > 0) {
        OH_LOG_INFO(LOG_APP, "[GetVideoHeight] Video height: %{public}lld", (long long)height);
    } else {
        OH_LOG_WARN(LOG_APP, "[GetVideoHeight] Video height not available (ret=%{public}d)", ret);
    }
    
    napi_value result;
    napi_create_int32(env, static_cast<int32_t>(height), &result);
    return result;
}

napi_value SetKeepAspect(napi_env env, napi_callback_info info) {
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[SetKeepAspect] ERROR: mpv not created");
        napi_value result;
        napi_get_undefined(env, &result);
        return result;
    }
    
    // 获取参数
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    
    if (argc < 2) {
        OH_LOG_ERROR(LOG_APP, "[SetKeepAspect] ERROR: Missing arguments");
        napi_value result;
        napi_get_undefined(env, &result);
        return result;
    }
    
    // 第二个参数是 boolean (keepAspect)
    bool keepAspect = false;
    napi_get_value_bool(env, args[1], &keepAspect);
    
    // 设置 MPV 的 keepaspect 属性
    const char* value = keepAspect ? "yes" : "no";
    int ret = mpv_set_property_string(global_mpv, "keepaspect", value);
    
    if (ret == 0) {
        OH_LOG_INFO(LOG_APP, "[SetKeepAspect] Set keepaspect=%{public}s successfully", value);
    } else {
        OH_LOG_ERROR(LOG_APP, "[SetKeepAspect] Failed to set keepaspect=%{public}s (ret=%{public}d)", value, ret);
    }
    
    napi_value result;
    napi_get_undefined(env, &result);
    return result;
}
