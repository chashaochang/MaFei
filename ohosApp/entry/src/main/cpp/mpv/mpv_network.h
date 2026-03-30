#ifndef MPV_NETWORK_H
#define MPV_NETWORK_H

#include <js_native_api.h>
#include <js_native_api_types.h>

// 获取网络速度信息
napi_value GetNetworkSpeed(napi_env env, napi_callback_info info);

#endif // MPV_NETWORK_H
