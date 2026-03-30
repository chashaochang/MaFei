#ifndef MPV_AUDIO_H
#define MPV_AUDIO_H

#include <napi/native_api.h>

#ifdef __cplusplus
extern "C" {
#endif

// 音频相关 NAPI 函数
napi_value GetAudioTracks(napi_env env, napi_callback_info info);
napi_value SelectAudio(napi_env env, napi_callback_info info);
napi_value GetCurrentAudioTrack(napi_env env, napi_callback_info info);

#ifdef __cplusplus
}
#endif

#endif // MPV_AUDIO_H
