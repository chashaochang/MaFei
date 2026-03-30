#ifndef MPV_PLAYBACK_H
#define MPV_PLAYBACK_H

#include <atomic>
#include <js_native_api.h>

// 播放控制函数
napi_value Seek(napi_env env, napi_callback_info info);
napi_value Play(napi_env env, napi_callback_info info);
napi_value Pause(napi_env env, napi_callback_info info);
napi_value SetSpeed(napi_env env, napi_callback_info info);
napi_value GetCurrentPosition(napi_env env, napi_callback_info info);
napi_value GetDuration(napi_env env, napi_callback_info info);
napi_value GetCacheDuration(napi_env env, napi_callback_info info);

// Duration 缓存管理（供 mpv_wrapper.cpp 使用）
extern double cached_duration;
extern bool duration_available;
extern std::atomic<bool> cached_pause;

// 重置所有缓存属性
void reset_all_cached_properties();

#endif // MPV_PLAYBACK_H
