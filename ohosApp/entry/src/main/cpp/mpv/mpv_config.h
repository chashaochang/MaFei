#ifndef MPV_CONFIG_H
#define MPV_CONFIG_H

#include <mpv/client.h>
#include <js_native_api.h>
#include <cstdint>

// 硬件解码模式
enum HwdecMode {
    HWDEC_MODE_BUFFER = 0,  // gpu-next + ohcodec-copy（buffer 模式，软解或帧复制）
    HWDEC_MODE_SURFACE = 1  // ohcodec-osd + hwdec=auto（surface 模式，硬件直渲）
};

// 解码类型（仅 Buffer 模式生效）
enum DecodeType {
    DECODE_TYPE_HW = 0,  // 硬件解码（默认）
    DECODE_TYPE_SW = 1   // 软件解码
};

// 配置 MPV 的各项参数
// 返回配置是否成功
bool ConfigureMPV(mpv_handle *mpv);

// 模式管理
HwdecMode GetHwdecMode();
void SetHwdecMode(HwdecMode mode);

// 解码类型管理（仅 Buffer 模式生效）
DecodeType GetDecodeType();
void SetDecodeType(DecodeType type);

// 缓存大小管理（MB）
int32_t GetCacheSize();
void SetCacheSize(int32_t size_mb);

// OSD Surface 管理（Surface 模式字幕叠加层）
uint64_t GetOsdSurfaceId();
uint64_t GetOsdSurfaceWidth();
uint64_t GetOsdSurfaceHeight();
void SetOsdSurface(uint64_t surfaceId, uint64_t width, uint64_t height);
void ClearOsdSurface();
napi_value SetOsdSurfaceNapi(napi_env env, napi_callback_info info);

// 字幕样式管理
int32_t GetSubtitleFontSize();
void SetSubtitleFontSize(int32_t size);
const char* GetSubtitleFont();
void SetSubtitleFont(const char* font);
const char* GetSubtitleColor();
void SetSubtitleColor(const char* color);
int32_t GetSubtitleShadow();
void SetSubtitleShadow(int32_t enabled);
int32_t GetSubtitlePos();
void SetSubtitlePos(int32_t pos);
napi_value SetSubtitleFontSizeNapi(napi_env env, napi_callback_info info);
napi_value GetSubtitleFontSizeNapi(napi_env env, napi_callback_info info);
napi_value SetSubtitleFontNapi(napi_env env, napi_callback_info info);
napi_value GetSubtitleFontNapi(napi_env env, napi_callback_info info);
napi_value SetSubtitleColorNapi(napi_env env, napi_callback_info info);
napi_value GetSubtitleColorNapi(napi_env env, napi_callback_info info);
napi_value SetSubtitleShadowNapi(napi_env env, napi_callback_info info);
napi_value SetSubtitlePosNapi(napi_env env, napi_callback_info info);
napi_value GetSubtitlePosNapi(napi_env env, napi_callback_info info);

// OSD 等级管理
int32_t GetOsdLevel();
void SetOsdLevel(int32_t level);
napi_value SetOsdLevelNapi(napi_env env, napi_callback_info info);
napi_value GetOsdLevelNapi(napi_env env, napi_callback_info info);

// 日志级别管理
void SetLogLevel(const char* level);
napi_value SetLogLevelNapi(napi_env env, napi_callback_info info);

// NAPI 函数
napi_value SetHwdecModeNapi(napi_env env, napi_callback_info info);
napi_value GetHwdecModeNapi(napi_env env, napi_callback_info info);
napi_value SetDecodeTypeNapi(napi_env env, napi_callback_info info);
napi_value GetDecodeTypeNapi(napi_env env, napi_callback_info info);
napi_value SetCacheSizeNapi(napi_env env, napi_callback_info info);
napi_value GetCacheSizeNapi(napi_env env, napi_callback_info info);

// 自动回退 buffer 模式（Surface 硬解不可用时自动触发）
bool GetAutoBufferFallback();
void SetAutoBufferFallback(bool fallback);
napi_value SetAutoBufferFallbackNapi(napi_env env, napi_callback_info info);
napi_value GetAutoBufferFallbackNapi(napi_env env, napi_callback_info info);

#endif // MPV_CONFIG_H
