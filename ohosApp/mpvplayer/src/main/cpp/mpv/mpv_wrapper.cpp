/**
 * mpv_wrapper.cpp - MPV 核心包装器（Surface 硬解 + OSD 字幕模式）
 * 
 * Surface 模式: vo=ohcodec-osd + hwdec=auto
 * - 后台初始化线程避免阻塞 UI
 * - 事件线程监听 hwdec-current 等属性变化
 * - 支持自动回退 buffer 模式
 */

#include "mpv_wrapper.h"
#include "mpv_config.h"
#include "mpv_playback.h"
#include "mpv_surface.h"
#include "mpv_loader.h"
#include <cstdint>
#include <string>
#include <cstring>
#include <cstdio>
#include <atomic>
#include <mutex>
#include <pthread.h>
#include <unistd.h>
#include <native_window/external_window.h>

#include <js_native_api.h>
#include <js_native_api_types.h>
#include <hilog/log.h>
#include "ace/xcomponent/native_interface_xcomponent.h"

#include <mpv/client.h>

#undef LOG_DOMAIN
#define LOG_DOMAIN 0xD003D00
#undef LOG_TAG
#define LOG_TAG "mpvnative"

mpv_handle *global_mpv = nullptr;
bool mpv_initialized = false;
std::atomic<bool> mpv_initialized_flag{false};

static std::atomic<uint64_t> g_event_thread_gen{0};
static pthread_t event_thread_id = 0;
static std::atomic<pthread_t> g_init_thread_id{0};
static std::atomic<bool> g_is_destroying{false};

struct EventThreadCtx {
    mpv_handle *mpv;
    uint64_t my_gen;
};

extern void *native_window;
extern uint64_t surfaceWidth;
extern uint64_t surfaceHeight;

struct DestroyContext {
    mpv_handle *mpv;
    pthread_t event_tid;
    pthread_t init_tid;
};

static void* async_destroy_thread(void* arg) {
    DestroyContext* ctx = static_cast<DestroyContext*>(arg);
    OH_LOG_INFO(LOG_APP, "[AsyncDestroy] Background cleanup started");

    if (ctx->init_tid != 0) {
        OH_LOG_INFO(LOG_APP, "[AsyncDestroy] Waiting for init thread...");
        pthread_join(ctx->init_tid, nullptr);
    }

    pthread_t final_event_tid = ctx->event_tid;
    if (final_event_tid == 0) {
        final_event_tid = event_thread_id;
    }
    if (final_event_tid != 0) {
        pthread_join(final_event_tid, nullptr);
    }

    OH_LOG_INFO(LOG_APP, "[AsyncDestroy] Terminating mpv...");
    mpv_terminate_destroy(ctx->mpv);
    OH_LOG_INFO(LOG_APP, "[AsyncDestroy] mpv terminated");

    delete ctx;
    g_is_destroying.store(false);
    return nullptr;
}

// ===== TSFN Callbacks =====

static napi_threadsafe_function g_init_tsfn = nullptr;
static napi_threadsafe_function g_tracklist_tsfn = nullptr;
static napi_threadsafe_function g_duration_tsfn = nullptr;
static napi_threadsafe_function g_end_file_tsfn = nullptr;
static napi_threadsafe_function g_hwdec_changed_tsfn = nullptr;
static napi_threadsafe_function g_paused_for_cache_tsfn = nullptr;
static napi_threadsafe_function g_loading_tsfn = nullptr;
static napi_threadsafe_function g_start_file_tsfn = nullptr;
static napi_threadsafe_function g_file_loaded_tsfn = nullptr;
static napi_threadsafe_function g_log_message_tsfn = nullptr;

static void js_void_callback(napi_env env, napi_value js_cb, void*, void*) {
    if (!env || !js_cb) return;
    napi_value u; napi_get_undefined(env, &u);
    napi_call_function(env, u, js_cb, 0, nullptr, nullptr);
}

static void js_bool_callback(napi_env env, napi_value js_cb, void*, void* data) {
    bool* val = static_cast<bool*>(data);
    if (!env || !js_cb) { delete val; return; }
    napi_value v; napi_get_boolean(env, val ? *val : false, &v);
    delete val;
    napi_value u; napi_get_undefined(env, &u);
    napi_call_function(env, u, js_cb, 1, &v, nullptr);
}

static bool setup_tsfn(napi_env env, napi_callback_info info, napi_threadsafe_function* tsfn,
    napi_threadsafe_function_call_js call_js, const char* name) {
    size_t argc = 1; napi_value args[1];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (*tsfn) { napi_release_threadsafe_function(*tsfn, napi_tsfn_release); *tsfn = nullptr; }
    if (argc < 1) return false;
    napi_valuetype vt; napi_typeof(env, args[0], &vt);
    if (vt != napi_function) return false;
    napi_value rn; napi_create_string_utf8(env, name, NAPI_AUTO_LENGTH, &rn);
    return napi_create_threadsafe_function(env, args[0], nullptr, rn, 0, 1, nullptr, nullptr, nullptr, call_js, tsfn) == napi_ok;
}

napi_value SetOnInitializedCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_init_tsfn, js_void_callback, "mpvInitCb");
    return nullptr;
}

void notify_js_initialized() {
    if (g_init_tsfn) {
        napi_status status = napi_call_threadsafe_function(g_init_tsfn, nullptr, napi_tsfn_blocking);
        if (status == napi_ok) {
            OH_LOG_INFO(LOG_APP, "[notify_js_initialized] Threadsafe callback queued");
        }
        napi_release_threadsafe_function(g_init_tsfn, napi_tsfn_release);
        g_init_tsfn = nullptr;
    }
}

napi_value SetOnTrackListChangedCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_tracklist_tsfn, js_void_callback, "mpvTrackListCb");
    return nullptr;
}

void notify_js_tracklist_changed() {
    if (g_tracklist_tsfn) {
        napi_call_threadsafe_function(g_tracklist_tsfn, nullptr, napi_tsfn_nonblocking);
    }
}

static void js_duration_callback(napi_env env, napi_value js_cb, void*, void* data) {
    double* dur = static_cast<double*>(data);
    if (!env || !js_cb) { delete dur; return; }
    napi_value v; napi_create_double(env, dur ? *dur : 0.0, &v);
    delete dur;
    napi_value u; napi_get_undefined(env, &u);
    napi_call_function(env, u, js_cb, 1, &v, nullptr);
}

napi_value SetOnDurationChangedCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_duration_tsfn, js_duration_callback, "mpvDurationCb");
    return nullptr;
}

void notify_js_duration_changed(double duration_ms) {
    if (g_duration_tsfn) {
        double* data = new double(duration_ms);
        napi_status status = napi_call_threadsafe_function(g_duration_tsfn, data, napi_tsfn_nonblocking);
        if (status != napi_ok) delete data;
    }
}

struct EndFileData {
    int reason;
    char error_str[128];
};

static void js_end_file_callback(napi_env env, napi_value js_cb, void*, void* data) {
    EndFileData* efd = static_cast<EndFileData*>(data);
    if (!env || !js_cb) { delete efd; return; }
    int reason = efd ? efd->reason : 0;
    const char* err_str = (efd && efd->error_str[0]) ? efd->error_str : "";
    delete efd;
    napi_value r, e;
    napi_create_int32(env, reason, &r);
    napi_create_string_utf8(env, err_str, NAPI_AUTO_LENGTH, &e);
    napi_value u; napi_get_undefined(env, &u);
    napi_value args[2] = { r, e };
    napi_call_function(env, u, js_cb, 2, args, nullptr);
}

napi_value SetOnEndFileCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_end_file_tsfn, js_end_file_callback, "mpvEndFileCb");
    return nullptr;
}

void notify_js_end_file(int reason, const char* error_str) {
    if (g_end_file_tsfn) {
        EndFileData* data = new EndFileData;
        data->reason = reason;
        if (error_str && error_str[0]) {
            strncpy(data->error_str, error_str, sizeof(data->error_str) - 1);
            data->error_str[sizeof(data->error_str) - 1] = '\0';
        } else {
            data->error_str[0] = '\0';
        }
        napi_call_threadsafe_function(g_end_file_tsfn, data, napi_tsfn_nonblocking);
    }
}

static void js_hwdec_changed_callback(napi_env env, napi_value js_cb, void*, void* data) {
    char* decoder = static_cast<char*>(data);
    if (!env || !js_cb) { delete[] decoder; return; }
    napi_value v; napi_create_string_utf8(env, decoder ? decoder : "unknown", NAPI_AUTO_LENGTH, &v);
    delete[] decoder;
    napi_value u; napi_get_undefined(env, &u);
    napi_call_function(env, u, js_cb, 1, &v, nullptr);
}

napi_value SetOnHwdecChangedCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_hwdec_changed_tsfn, js_hwdec_changed_callback, "mpvHwdecCb");
    return nullptr;
}

void notify_js_hwdec_changed(const char* decoder_name) {
    if (g_hwdec_changed_tsfn) {
        size_t len = decoder_name ? strlen(decoder_name) : 0;
        char* data = new char[len + 1];
        if (decoder_name && len > 0) strncpy(data, decoder_name, len);
        data[len] = '\0';
        napi_call_threadsafe_function(g_hwdec_changed_tsfn, data, napi_tsfn_nonblocking);
    }
}

napi_value SetOnPausedForCacheCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_paused_for_cache_tsfn, js_bool_callback, "mpvPausedCacheCb");
    return nullptr;
}

void notify_js_paused_for_cache(bool paused) {
    if (g_paused_for_cache_tsfn) {
        bool* data = new bool(paused);
        napi_call_threadsafe_function(g_paused_for_cache_tsfn, data, napi_tsfn_nonblocking);
    }
}

napi_value SetOnLoadingCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_loading_tsfn, js_bool_callback, "mpvLoadingCb");
    return nullptr;
}

void notify_js_loading(bool loading) {
    if (g_loading_tsfn) {
        bool* data = new bool(loading);
        napi_call_threadsafe_function(g_loading_tsfn, data, napi_tsfn_nonblocking);
    }
}

napi_value SetOnStartFileCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_start_file_tsfn, js_void_callback, "mpvStartFileCb");
    return nullptr;
}

void notify_js_start_file() {
    if (g_start_file_tsfn) {
        napi_call_threadsafe_function(g_start_file_tsfn, nullptr, napi_tsfn_nonblocking);
    }
}

napi_value SetOnFileLoadedCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_file_loaded_tsfn, js_void_callback, "mpvFileLoadedCb");
    return nullptr;
}

void notify_js_file_loaded() {
    if (g_file_loaded_tsfn) {
        napi_call_threadsafe_function(g_file_loaded_tsfn, nullptr, napi_tsfn_nonblocking);
    }
}

struct LogMessageData {
    char prefix[64];
    int level;
    char text[512];
};

static void js_log_message_callback(napi_env env, napi_value js_cb, void*, void* data) {
    LogMessageData* lmd = static_cast<LogMessageData*>(data);
    if (!env || !js_cb) { delete lmd; return; }
    const char* level_str;
    switch (lmd->level) {
        case MPV_LOG_LEVEL_FATAL: level_str = "fatal"; break;
        case MPV_LOG_LEVEL_ERROR: level_str = "error"; break;
        case MPV_LOG_LEVEL_WARN: level_str = "warn"; break;
        case MPV_LOG_LEVEL_INFO: level_str = "info"; break;
        default: level_str = "debug"; break;
    }
    napi_value pv, lv, tv;
    napi_create_string_utf8(env, lmd->prefix, NAPI_AUTO_LENGTH, &pv);
    napi_create_string_utf8(env, level_str, NAPI_AUTO_LENGTH, &lv);
    napi_create_string_utf8(env, lmd->text, NAPI_AUTO_LENGTH, &tv);
    delete lmd;
    napi_value u; napi_get_undefined(env, &u);
    napi_value args[3] = { lv, pv, tv };
    napi_call_function(env, u, js_cb, 3, args, nullptr);
}

napi_value SetOnLogMessageCallback(napi_env env, napi_callback_info info) {
    setup_tsfn(env, info, &g_log_message_tsfn, js_log_message_callback, "mpvLogMsgCb");
    return nullptr;
}

void notify_js_log_message(const char* prefix, int level, const char* text) {
    if (g_log_message_tsfn) {
        LogMessageData* data = new LogMessageData;
        strncpy(data->prefix, prefix ? prefix : "?", sizeof(data->prefix) - 1);
        data->prefix[sizeof(data->prefix) - 1] = '\0';
        data->level = level;
        if (text) {
            strncpy(data->text, text, sizeof(data->text) - 1);
            data->text[sizeof(data->text) - 1] = '\0';
        } else {
            data->text[0] = '\0';
        }
        napi_call_threadsafe_function(g_log_message_tsfn, data, napi_tsfn_nonblocking);
    }
}

// ===== Event Thread =====

static void* event_thread(void* arg) {
    EventThreadCtx* ctx = static_cast<EventThreadCtx*>(arg);
    mpv_handle* mpv = ctx->mpv;
    uint64_t my_gen = ctx->my_gen;
    delete ctx;

    OH_LOG_INFO(LOG_APP, "[EventThread] Started (gen=%{public}llu)", (unsigned long long)my_gen);

    while (true) {
        if (g_event_thread_gen.load(std::memory_order_relaxed) != my_gen) {
            OH_LOG_INFO(LOG_APP, "[EventThread] Generation changed, exiting");
            break;
        }

        mpv_event* event = mpv_wait_event(mpv, -1.0);

        if (g_event_thread_gen.load(std::memory_order_relaxed) != my_gen) {
            break;
        }

        if (!event || event->event_id == MPV_EVENT_NONE) {
            continue;
        }

        switch (event->event_id) {
            case MPV_EVENT_LOG_MESSAGE: {
                mpv_event_log_message* msg = static_cast<mpv_event_log_message*>(event->data);
                if (msg && msg->text) {
                    notify_js_log_message(msg->prefix ? msg->prefix : "?",
                                         msg->log_level, msg->text);
                }
                break;
            }
            case MPV_EVENT_PROPERTY_CHANGE: {
                mpv_event_property* prop = static_cast<mpv_event_property*>(event->data);
                if (prop && prop->name) {
                    if (strcmp(prop->name, "duration/full") == 0) {
                        if (prop->format == MPV_FORMAT_DOUBLE && prop->data) {
                            double new_duration = *(double*)prop->data;
                            if (new_duration > 0) {
                                cached_duration = new_duration;
                                duration_available = true;
                                notify_js_duration_changed(new_duration * 1000.0);
                            }
                        }
                    }
                    else if (strcmp(prop->name, "hwdec-current") == 0) {
                        if (prop->format == MPV_FORMAT_STRING && prop->data) {
                            const char* val = *(const char**)prop->data;
                            if (val && strlen(val) > 0) {
                                OH_LOG_INFO(LOG_APP, "[EventThread] hwdec-current: %{public}s", val);
                                notify_js_hwdec_changed(val);
                            }
                        }
                    }
                    else if (strcmp(prop->name, "paused-for-cache") == 0) {
                        if (prop->format == MPV_FORMAT_FLAG && prop->data) {
                            bool val = *(int*)prop->data;
                            notify_js_paused_for_cache(val);
                        }
                    }
                    else if (strcmp(prop->name, "core-idle") == 0) {
                        if (prop->format == MPV_FORMAT_FLAG && prop->data) {
                            bool idle = *(int*)prop->data;
                            bool is_paused = cached_pause.load(std::memory_order_relaxed);
                            notify_js_loading(idle && !is_paused);
                        }
                    }
                    else if (strcmp(prop->name, "pause") == 0) {
                        if (prop->format == MPV_FORMAT_FLAG && prop->data) {
                            cached_pause.store(*(int*)prop->data != 0, std::memory_order_relaxed);
                        }
                    }
                    else if (strcmp(prop->name, "track-list") == 0) {
                        notify_js_tracklist_changed();
                    }
                }
                break;
            }
            case MPV_EVENT_SHUTDOWN:
                OH_LOG_INFO(LOG_APP, "[EventThread] SHUTDOWN event");
                g_event_thread_gen.fetch_add(1, std::memory_order_relaxed);
                return nullptr;
            case MPV_EVENT_END_FILE: {
                mpv_event_end_file* ef = static_cast<mpv_event_end_file*>(event->data);
                int reason = ef ? ef->reason : 0;
                OH_LOG_INFO(LOG_APP, "[EventThread] END_FILE reason=%{public}d", reason);
                if (reason == MPV_END_FILE_REASON_EOF || reason == MPV_END_FILE_REASON_ERROR) {
                    const char* err_str = (reason == MPV_END_FILE_REASON_ERROR && ef && ef->error != 0)
                        ? mpv_error_string(ef->error) : "";
                    notify_js_end_file(reason, err_str);
                }
                break;
            }
            case MPV_EVENT_START_FILE:
                notify_js_start_file();
                break;
            case MPV_EVENT_FILE_LOADED:
                notify_js_file_loaded();
                break;
            default:
                break;
        }
    }

    OH_LOG_INFO(LOG_APP, "[EventThread] Stopped");
    return nullptr;
}

void start_event_thread() {
    uint64_t my_gen = g_event_thread_gen.load(std::memory_order_relaxed);
    EventThreadCtx* ctx = new EventThreadCtx{global_mpv, my_gen};
    if (pthread_create(&event_thread_id, nullptr, event_thread, ctx) != 0) {
        OH_LOG_ERROR(LOG_APP, "[start_event_thread] Failed to create thread");
        delete ctx;
        return;
    }
    pthread_setname_np(event_thread_id, "mpv_event");
    OH_LOG_INFO(LOG_APP, "[start_event_thread] Thread created (gen=%{public}llu)", (unsigned long long)my_gen);
}

void stop_event_thread() {
    event_thread_id = 0;
}

uint64_t get_event_gen() {
    return g_event_thread_gen.load(std::memory_order_relaxed);
}

void register_init_thread(pthread_t tid) {
    g_init_thread_id.store(tid, std::memory_order_relaxed);
}

static void release_all_callbacks() {
    napi_threadsafe_function* all[] = {
        &g_init_tsfn, &g_tracklist_tsfn, &g_duration_tsfn, &g_end_file_tsfn,
        &g_hwdec_changed_tsfn, &g_paused_for_cache_tsfn, &g_loading_tsfn,
        &g_start_file_tsfn, &g_file_loaded_tsfn, &g_log_message_tsfn
    };
    for (auto* p : all) {
        if (*p) { napi_release_threadsafe_function(*p, napi_tsfn_release); *p = nullptr; }
    }
}

// ===== NAPI Functions =====

napi_value Create(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [Create] called");

    if (g_is_destroying.load()) {
        OH_LOG_WARN(LOG_APP, "[Create] Previous destroy still running, skip blocking create");
        return nullptr;
    }

    if (global_mpv) {
        OH_LOG_WARN(LOG_APP, "[Create] Found stale mpv instance, cleaning up");
        mpv_handle* old_mpv = global_mpv;
        global_mpv = nullptr;
        mpv_initialized = false;
        mpv_initialized_flag.store(false);

        if (event_thread_id != 0) {
            g_event_thread_gen.fetch_add(1, std::memory_order_relaxed);
            mpv_wakeup(old_mpv);
            pthread_join(event_thread_id, nullptr);
            event_thread_id = 0;
        }
        mpv_terminate_destroy(old_mpv);
    }

    global_mpv = mpv_create();
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[Create] ERROR: mpv_create failed");
        return nullptr;
    }

    if (!ConfigureMPV(global_mpv)) {
        OH_LOG_ERROR(LOG_APP, "[Create] ERROR: ConfigureMPV failed");
        mpv_terminate_destroy(global_mpv);
        global_mpv = nullptr;
        return nullptr;
    }

    if (native_window) {
        uint64_t surfaceId = 0;
        if (OH_NativeWindow_GetSurfaceId((OHNativeWindow*)native_window, &surfaceId) != 0) {
            OH_LOG_ERROR(LOG_APP, "[Create] Failed to get surface ID");
            mpv_terminate_destroy(global_mpv);
            global_mpv = nullptr;
            return nullptr;
        }

        InitThreadArgs* args = new InitThreadArgs{global_mpv, surfaceId, surfaceWidth, surfaceHeight};
        pthread_t init_thread;
        if (pthread_create(&init_thread, nullptr, mpv_init_thread, args) != 0) {
            OH_LOG_ERROR(LOG_APP, "[Create] Failed to create init thread");
            mpv_init_thread(args);
        } else {
            g_init_thread_id.store(init_thread, std::memory_order_relaxed);
        }
    } else {
        OH_LOG_INFO(LOG_APP, "[Create] Waiting for OnSurfaceCreated");
    }

    cached_duration = 0.0;
    duration_available = false;

    napi_value result;
    napi_create_int64(env, (int64_t)global_mpv, &result);
    return result;
}

napi_value IsInitialized(napi_env env, napi_callback_info info) {
    napi_value result;
    napi_get_boolean(env, mpv_initialized_flag.load(std::memory_order_acquire), &result);
    return result;
}

napi_value IsDestroying(napi_env env, napi_callback_info info) {
    napi_value result;
    napi_get_boolean(env, g_is_destroying.load(std::memory_order_acquire), &result);
    return result;
}

napi_value Destroy(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [Destroy] called");

    if (g_is_destroying.load() || !global_mpv) {
        return nullptr;
    }

    g_is_destroying.store(true);
    mpv_initialized = false;
    mpv_initialized_flag.store(false);

    mpv_handle* mpv_to_destroy = global_mpv;
    pthread_t event_tid_copy = event_thread_id;
    pthread_t init_tid_copy = g_init_thread_id.load(std::memory_order_relaxed);

    global_mpv = nullptr;
    event_thread_id = 0;
    g_init_thread_id.store(0, std::memory_order_relaxed);

    g_event_thread_gen.fetch_add(1, std::memory_order_relaxed);
    if (event_tid_copy != 0) {
        mpv_wakeup(mpv_to_destroy);
    }

    DestroyContext* ctx = new DestroyContext();
    ctx->mpv = mpv_to_destroy;
    ctx->event_tid = event_tid_copy;
    ctx->init_tid = init_tid_copy;

    pthread_t cleanup_thread;
    if (pthread_create(&cleanup_thread, nullptr, async_destroy_thread, ctx) == 0) {
        pthread_detach(cleanup_thread);
    } else {
        async_destroy_thread(ctx);
    }

    release_all_callbacks();
    cached_duration = 0.0;
    duration_available = false;
    ClearOsdSurface();

    OH_LOG_INFO(LOG_APP, ">>> [Destroy] Returned");
    return nullptr;
}

napi_value Reset(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [Reset] called");

    if (!global_mpv) {
        return nullptr;
    }

    const char* stop_cmd[] = {"stop", nullptr};
    mpv_command(global_mpv, stop_cmd);

    napi_value result;
    napi_get_boolean(env, true, &result);
    return result;
}

napi_value Command(napi_env env, napi_callback_info info) {
    if (!global_mpv) {
        return nullptr;
    }

    if (!mpv_initialized) {
        napi_value return_obj;
        napi_create_object(env, &return_obj);
        napi_value success_value, code_value, error_value;
        napi_get_boolean(env, false, &success_value);
        napi_create_int32(env, -1, &code_value);
        napi_create_string_utf8(env, "core not initialized", NAPI_AUTO_LENGTH, &error_value);
        napi_set_named_property(env, return_obj, "success", success_value);
        napi_set_named_property(env, return_obj, "code", code_value);
        napi_set_named_property(env, return_obj, "error", error_value);
        return return_obj;
    }

    size_t argc = 2;
    napi_value args[2];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 2) {
        return nullptr;
    }

    bool is_array = false;
    napi_is_array(env, args[1], &is_array);
    if (!is_array) {
        return nullptr;
    }

    uint32_t array_length = 0;
    napi_get_array_length(env, args[1], &array_length);
    if (array_length == 0 || array_length > 32) {
        return nullptr;
    }

    const char** cmd = new const char*[array_length + 1];
    char** buffers = new char*[array_length];

    for (uint32_t i = 0; i < array_length; i++) {
        napi_value element;
        napi_get_element(env, args[1], i, &element);
        size_t str_len = 0;
        napi_get_value_string_utf8(env, element, nullptr, 0, &str_len);
        buffers[i] = new char[str_len + 1];
        napi_get_value_string_utf8(env, element, buffers[i], str_len + 1, &str_len);
        cmd[i] = buffers[i];
    }
    cmd[array_length] = nullptr;

    int result = mpv_command(global_mpv, cmd);

    for (uint32_t i = 0; i < array_length; i++) {
        delete[] buffers[i];
    }
    delete[] buffers;
    delete[] cmd;

    napi_value return_obj;
    napi_create_object(env, &return_obj);
    napi_value success_value, code_value;
    napi_get_boolean(env, result >= 0, &success_value);
    napi_create_int32(env, result, &code_value);
    napi_set_named_property(env, return_obj, "success", success_value);
    napi_set_named_property(env, return_obj, "code", code_value);

    if (result < 0) {
        napi_value error_value;
        napi_create_string_utf8(env, mpv_error_string(result), NAPI_AUTO_LENGTH, &error_value);
        napi_set_named_property(env, return_obj, "error", error_value);
    }

    return return_obj;
}
