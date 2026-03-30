#ifndef MPV_WRAPPER_H
#define MPV_WRAPPER_H

#include <napi/native_api.h>
#include "ace/xcomponent/native_interface_xcomponent.h"
#include <mpv/client.h>
#include <atomic>
#include <pthread.h>

#ifdef __cplusplus
extern "C" {
#endif

// ==================== 核心 NAPI 函数 ====================
napi_value Create(napi_env env, napi_callback_info info);
napi_value Destroy(napi_env env, napi_callback_info info);
napi_value Command(napi_env env, napi_callback_info info);
napi_value IsInitialized(napi_env env, napi_callback_info info);
napi_value IsDestroying(napi_env env, napi_callback_info info);
napi_value Reset(napi_env env, napi_callback_info info);

// ==================== 全局状态 ====================
extern mpv_handle *global_mpv;
extern bool mpv_initialized;
extern std::atomic<bool> mpv_initialized_flag;

// ==================== 事件处理线程 ====================
void start_event_thread();
void stop_event_thread();
void register_init_thread(pthread_t tid);
uint64_t get_event_gen();

// ==================== 初始化完成回调 ====================
napi_value SetOnInitializedCallback(napi_env env, napi_callback_info info);
void notify_js_initialized();

// ==================== track-list 变化回调 ====================
napi_value SetOnTrackListChangedCallback(napi_env env, napi_callback_info info);
void notify_js_tracklist_changed();

// ==================== duration 变化回调 ====================
napi_value SetOnDurationChangedCallback(napi_env env, napi_callback_info info);
void notify_js_duration_changed(double duration_ms);

// ==================== 播放结束回调 ====================
napi_value SetOnEndFileCallback(napi_env env, napi_callback_info info);
void notify_js_end_file(int reason, const char* error_str);

// ==================== 硬解状态变化回调 ====================
napi_value SetOnHwdecChangedCallback(napi_env env, napi_callback_info info);
void notify_js_hwdec_changed(const char* decoder_name);

// ==================== 缓冲暂停回调 ====================
napi_value SetOnPausedForCacheCallback(napi_env env, napi_callback_info info);
void notify_js_paused_for_cache(bool paused);

// ==================== loading 状态回调 ====================
napi_value SetOnLoadingCallback(napi_env env, napi_callback_info info);
void notify_js_loading(bool loading);

// ==================== START_FILE 回调 ====================
napi_value SetOnStartFileCallback(napi_env env, napi_callback_info info);
void notify_js_start_file();

// ==================== FILE_LOADED 回调 ====================
napi_value SetOnFileLoadedCallback(napi_env env, napi_callback_info info);
void notify_js_file_loaded();

// ==================== 日志消息回调 ====================
napi_value SetOnLogMessageCallback(napi_env env, napi_callback_info info);
void notify_js_log_message(const char* prefix, int level, const char* text);

#ifdef __cplusplus
}
#endif

#endif // MPV_WRAPPER_H
