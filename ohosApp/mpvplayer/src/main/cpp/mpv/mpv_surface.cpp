/**
 * mpv_surface.cpp - XComponent 回调和 Surface 管理（Surface 硬解模式）
 * 
 * Surface 模式: vo=ohcodec-osd + hwdec=auto
 * - 设置 wid 和 ohos-surface-size 给 mpv
 * - 后台线程执行 mpv_initialize 避免阻塞 UI
 * - OSD 字幕 surface 叠加层支持
 */

#include "mpv_surface.h"
#include "mpv_wrapper.h"
#include "mpv_playback.h"
#include "mpv_config.h"
#include <mpv/client.h>
#include <hilog/log.h>
#include <cstring>
#include <cstdio>
#include <pthread.h>
#include <atomic>
#include <mutex>
#include <native_window/external_window.h>

#undef LOG_DOMAIN
#define LOG_DOMAIN 0xD003D00
#undef LOG_TAG
#define LOG_TAG "mpvsurface"

uint64_t surfaceWidth = 1280;
uint64_t surfaceHeight = 720;
void *native_window = nullptr;

extern mpv_handle *global_mpv;
extern bool mpv_initialized;
extern std::atomic<bool> mpv_initialized_flag;
extern std::mutex g_config_mutex;

extern void start_event_thread();
extern void notify_js_initialized();
extern uint64_t get_event_gen();

static std::atomic<pthread_t> g_init_thread_id{0};

void* mpv_init_thread(void* arg) {
    InitThreadArgs* args = static_cast<InitThreadArgs*>(arg);
    OH_LOG_INFO(LOG_APP, "[mpv_init_thread] START");

    const uint64_t my_gen = get_event_gen();

    char surface_size_str[64];
    snprintf(surface_size_str, sizeof(surface_size_str), "%llux%llu",
             (unsigned long long)args->width, (unsigned long long)args->height);
    mpv_set_option_string(args->mpv, "ohos-surface-size", surface_size_str);

    char wid_str[64];
    snprintf(wid_str, sizeof(wid_str), "%llu", (unsigned long long)args->surfaceId);
    int wid_ret = mpv_set_option_string(args->mpv, "wid", wid_str);
    if (wid_ret < 0) {
        OH_LOG_ERROR(LOG_APP, "[mpv_init_thread] ERROR: Failed to set wid: %{public}s", mpv_error_string(wid_ret));
        delete args;
        return nullptr;
    }

    uint64_t osd_sid = GetOsdSurfaceId();
    if (osd_sid > 0) {
        char osd_sid_str[64];
        snprintf(osd_sid_str, sizeof(osd_sid_str), "%llu", (unsigned long long)osd_sid);
        char osd_w_str[32], osd_h_str[32];
        snprintf(osd_w_str, sizeof(osd_w_str), "%llu", (unsigned long long)GetOsdSurfaceWidth());
        snprintf(osd_h_str, sizeof(osd_h_str), "%llu", (unsigned long long)GetOsdSurfaceHeight());
        mpv_set_option_string(args->mpv, "ohcodec-osd-surface-id", osd_sid_str);
        mpv_set_option_string(args->mpv, "ohcodec-osd-width", osd_w_str);
        mpv_set_option_string(args->mpv, "ohcodec-osd-height", osd_h_str);
        OH_LOG_INFO(LOG_APP, "[mpv_init_thread] OSD surface pre-set: id=%{public}s, size=%{public}sx%{public}s",
                    osd_sid_str, osd_w_str, osd_h_str);
    }

    int init_ret = mpv_initialize(args->mpv);
    if (init_ret < 0) {
        OH_LOG_ERROR(LOG_APP, "[mpv_init_thread] ERROR: mpv_initialize failed: %{public}s", mpv_error_string(init_ret));
        notify_js_initialized();
        delete args;
        return nullptr;
    }

    if (get_event_gen() != my_gen) {
        OH_LOG_WARN(LOG_APP, "[mpv_init_thread] Aborted after initialize: gen changed");
        delete args;
        return nullptr;
    }

    mpv_observe_property(args->mpv, 0, "duration/full", MPV_FORMAT_DOUBLE);
    mpv_observe_property(args->mpv, 1, "sid", MPV_FORMAT_INT64);
    mpv_observe_property(args->mpv, 2, "cache-buffering-state", MPV_FORMAT_INT64);
    mpv_observe_property(args->mpv, 3, "demuxer-cache-duration", MPV_FORMAT_DOUBLE);
    mpv_observe_property(args->mpv, 4, "time-pos", MPV_FORMAT_DOUBLE);
    mpv_observe_property(args->mpv, 5, "cache-speed", MPV_FORMAT_DOUBLE);
    mpv_observe_property(args->mpv, 6, "hwdec-current", MPV_FORMAT_STRING);
    mpv_observe_property(args->mpv, 7, "width", MPV_FORMAT_INT64);
    mpv_observe_property(args->mpv, 8, "height", MPV_FORMAT_INT64);
    mpv_observe_property(args->mpv, 9, "track-list", MPV_FORMAT_NODE);
    mpv_observe_property(args->mpv, 10, "video-format", MPV_FORMAT_STRING);
    mpv_observe_property(args->mpv, 11, "estimated-vf-fps", MPV_FORMAT_DOUBLE);
    mpv_observe_property(args->mpv, 12, "video-bitrate", MPV_FORMAT_DOUBLE);
    mpv_observe_property(args->mpv, 13, "aid", MPV_FORMAT_INT64);
    mpv_observe_property(args->mpv, 14, "pause", MPV_FORMAT_FLAG);
    mpv_observe_property(args->mpv, 15, "paused-for-cache", MPV_FORMAT_FLAG);
    mpv_observe_property(args->mpv, 16, "core-idle", MPV_FORMAT_FLAG);

    reset_all_cached_properties();

    if (get_event_gen() != my_gen) {
        OH_LOG_WARN(LOG_APP, "[mpv_init_thread] Aborted before event thread start: gen changed");
        delete args;
        return nullptr;
    }

    start_event_thread();

    mpv_initialized_flag.store(true, std::memory_order_release);
    mpv_initialized = true;
    OH_LOG_INFO(LOG_APP, "[mpv_init_thread] ✓ MPV initialized");

    if (surfaceWidth != args->width || surfaceHeight != args->height) {
        char updated_size_str[64];
        snprintf(updated_size_str, sizeof(updated_size_str), "%llux%llu",
                 (unsigned long long)surfaceWidth, (unsigned long long)surfaceHeight);
        const char *size_cmd[] = {"set", "ohos-surface-size", updated_size_str, nullptr};
        mpv_command_async(args->mpv, 0, size_cmd);
    }

    notify_js_initialized();
    OH_LOG_INFO(LOG_APP, "[mpv_init_thread] DONE");

    g_init_thread_id.store(0, std::memory_order_relaxed);
    delete args;
    return nullptr;
}

void OnSurfaceCreated(OH_NativeXComponent *component, void *window) {
    OH_LOG_INFO(LOG_APP, "=== [OnSurfaceCreated] START ===");

    if (!window) {
        OH_LOG_ERROR(LOG_APP, "[OnSurfaceCreated] ERROR: window is null");
        return;
    }

    native_window = window;

    OH_NativeXComponent_GetXComponentSize(component, window, &surfaceWidth, &surfaceHeight);
    OH_LOG_INFO(LOG_APP, "[OnSurfaceCreated] size=%{public}llux%{public}llu",
                (unsigned long long)surfaceWidth, (unsigned long long)surfaceHeight);

    if (!global_mpv) {
        OH_LOG_WARN(LOG_APP, "[OnSurfaceCreated] MPV not created yet, window saved");
        return;
    }

    OH_LOG_INFO(LOG_APP, "[OnSurfaceCreated] MPV already created, initializing in background...");

    uint64_t surfaceId = 0;
    int surfaceIdRet = OH_NativeWindow_GetSurfaceId((OHNativeWindow*)native_window, &surfaceId);
    if (surfaceIdRet != 0) {
        OH_LOG_ERROR(LOG_APP, "[OnSurfaceCreated] ERROR: OH_NativeWindow_GetSurfaceId failed: %{public}d", surfaceIdRet);
        return;
    }
    OH_LOG_INFO(LOG_APP, "[OnSurfaceCreated] surfaceId=%{public}llu", (unsigned long long)surfaceId);

    InitThreadArgs* args = new InitThreadArgs{global_mpv, surfaceId, surfaceWidth, surfaceHeight};
    pthread_t init_thread;
    if (pthread_create(&init_thread, nullptr, mpv_init_thread, args) != 0) {
        OH_LOG_ERROR(LOG_APP, "[OnSurfaceCreated] ERROR: Failed to create init thread, falling back to sync");
        mpv_init_thread(args);
    } else {
        g_init_thread_id.store(init_thread, std::memory_order_relaxed);
        OH_LOG_INFO(LOG_APP, "[OnSurfaceCreated] Init thread started");
    }

    OH_LOG_INFO(LOG_APP, "=== [OnSurfaceCreated] RETURNED ===");
}

void OnSurfaceDestroyed(OH_NativeXComponent *component, void *window) {
    OH_LOG_INFO(LOG_APP, "[OnSurfaceDestroyed] called");
    native_window = nullptr;
    OH_LOG_INFO(LOG_APP, "[OnSurfaceDestroyed] done");
}

void OnSurfaceChanged(OH_NativeXComponent *component, void *window) {
    uint64_t newWidth = 0, newHeight = 0;
    OH_NativeXComponent_GetXComponentSize(component, window, &newWidth, &newHeight);

    if (newWidth != surfaceWidth || newHeight != surfaceHeight) {
        surfaceWidth = newWidth;
        surfaceHeight = newHeight;
        OH_LOG_INFO(LOG_APP, "[OnSurfaceChanged] new size=%{public}llux%{public}llu",
                    (unsigned long long)surfaceWidth, (unsigned long long)surfaceHeight);

        mpv_handle *mpv = global_mpv;
        if (mpv && mpv_initialized) {
            char size_str[64];
            snprintf(size_str, sizeof(size_str), "%llux%llu",
                     (unsigned long long)surfaceWidth, (unsigned long long)surfaceHeight);
            const char *cmd[] = {"set", "ohos-surface-size", size_str, nullptr};
            mpv_command_async(mpv, 0, cmd);
        }
    }
}
