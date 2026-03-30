#ifndef MPV_SUBTITLE_H
#define MPV_SUBTITLE_H

#include <napi/native_api.h>

#ifdef __cplusplus
extern "C" {
#endif

// 字幕相关 NAPI 函数
napi_value GetSubtitleTracks(napi_env env, napi_callback_info info);
napi_value SelectSubtitle(napi_env env, napi_callback_info info);
napi_value SetSubtitleStyle(napi_env env, napi_callback_info info);
napi_value GetCurrentSubtitleTrack(napi_env env, napi_callback_info info);

#ifdef __cplusplus
}
#endif

#endif // MPV_SUBTITLE_H
