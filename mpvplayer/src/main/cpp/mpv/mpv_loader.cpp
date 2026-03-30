#include "mpv_loader.h"
#include "mpv_wrapper.h"
#include "mpv_playback.h"
#include <mpv/client.h>
#include <hilog/log.h>
#include <cstdio>
#include <cstring>

// Hilog domain/tag
#undef LOG_DOMAIN
#define LOG_DOMAIN 0xD003D00
#undef LOG_TAG
#define LOG_TAG "mpvloader"

// 引用全局 MPV 句柄（已在 mpv_wrapper.h 中声明）
// extern mpv_handle *global_mpv;
// extern bool mpv_initialized;

// 引用 duration 缓存
extern double cached_duration;
extern bool duration_available;

napi_value LoadVideo(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [LoadVideo] called");
    
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[LoadVideo] ERROR: mpv not created");
        return nullptr;
    }
    
    // 检查 MPV 是否已初始化
    if (!mpv_initialized) {
        OH_LOG_ERROR(LOG_APP, "[LoadVideo] ERROR: MPV not initialized yet! mpv_initialized=%d", mpv_initialized);
        OH_LOG_ERROR(LOG_APP, "[LoadVideo] Make sure OnSurfaceCreated has been called before LoadVideo");
        return nullptr;
    }
    
    OH_LOG_INFO(LOG_APP, "[LoadVideo] MPV initialized, proceeding with loadfile...");
    
    // Accept (mpvHandle, url, [startPosition])
    size_t argc = 3;
    napi_value args[3];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 2) {
        OH_LOG_ERROR(LOG_APP, "[LoadVideo] ERROR: missing parameters, argc=%zu", argc);
        return nullptr;
    }

    char url[2048];
    size_t url_len;
    napi_get_value_string_utf8(env, args[1], url, sizeof(url), &url_len);
    OH_LOG_INFO(LOG_APP, "[LoadVideo] URL: %{public}s", url);
    
    // 如果提供了起始位置（秒），在加载前设置 start 选项
    if (argc >= 3) {
        napi_valuetype type;
        napi_typeof(env, args[2], &type);
        if (type == napi_number) {
            double startPos = 0;
            napi_get_value_double(env, args[2], &startPos);
            if (startPos > 0) {
                OH_LOG_INFO(LOG_APP, "[LoadVideo] ⏩ Setting start position: %{public}f seconds", startPos);
                char start_value[32];
                snprintf(start_value, sizeof(start_value), "%.3f", startPos);
                mpv_set_option_string(global_mpv, "start", start_value);
            }
        }
    }
    
    // Load file
    OH_LOG_INFO(LOG_APP, "[LoadVideo] Sending loadfile command...");
    const char *cmd[] = {"loadfile", url, nullptr};
    int result = mpv_command(global_mpv, cmd);
    if (result != 0) {
        OH_LOG_ERROR(LOG_APP, "[LoadVideo] ERROR: mpv_command(loadfile) failed: %{public}d", result);
        OH_LOG_ERROR(LOG_APP, "[LoadVideo] Error string: %{public}s", mpv_error_string(result));
        return nullptr;
    }
    
    OH_LOG_INFO(LOG_APP, ">>> [LoadVideo] loadfile command sent successfully");
    
    // 快速消费少量事件（避免阻塞主线程，限制最多10次迭代）
    mpv_event *event;
    int event_count = 0;
    const int max_events = 10;
    while (event_count < max_events && (event = mpv_wait_event(global_mpv, 0)) && event->event_id != MPV_EVENT_NONE) {
        if (event->event_id == MPV_EVENT_LOG_MESSAGE) {
            // 只打印前5条关键日志
            if (event_count < 5) {
                mpv_event_log_message *msg = (mpv_event_log_message *)event->data;
                if (msg && msg->text && msg->log_level <= MPV_LOG_LEVEL_WARN) {
                    OH_LOG_INFO(LOG_APP, "[MPV/%{public}s] %{public}s", 
                               msg->prefix ? msg->prefix : "?", msg->text);
                }
            }
        }
        event_count++;
    }
    
    // 快速检查是否暂停（必需操作）
    int pause_state = 0;
    if (mpv_get_property(global_mpv, "pause", MPV_FORMAT_FLAG, &pause_state) == 0 && pause_state) {
        const char *play_cmd[] = {"set", "pause", "no", nullptr};
        mpv_command(global_mpv, play_cmd);
    }
    
    // 尝试快速获取 duration（非阻塞，失败也不影响，事件线程会更新）
    double duration = 0;
    int duration_ret = mpv_get_property(global_mpv, "duration/full", MPV_FORMAT_DOUBLE, &duration);
    if (duration_ret == 0 && duration > 0) {
        cached_duration = duration;
        duration_available = true;
        OH_LOG_INFO(LOG_APP, "[LoadVideo] ✓ Duration: %{public}.1fs", duration);
    }
    
    return nullptr;
}
