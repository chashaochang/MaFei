#ifndef MPV_VIDEO_INFO_H
#define MPV_VIDEO_INFO_H

#include <napi/native_api.h>

#ifdef __cplusplus
extern "C" {
#endif

// 视频信息相关 NAPI 函数
napi_value GetVideoWidth(napi_env env, napi_callback_info info);
napi_value GetVideoHeight(napi_env env, napi_callback_info info);
napi_value GetHardwareDecoder(napi_env env, napi_callback_info info);
napi_value SetKeepAspect(napi_env env, napi_callback_info info);

#ifdef __cplusplus
}
#endif

#endif // MPV_VIDEO_INFO_H
