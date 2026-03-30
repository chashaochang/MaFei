/**
 * mpv_config.cpp - MPV 配置（支持 Surface 硬解 + OSD 字幕模式）
 * 
 * Surface 模式: vo=ohcodec-osd + hwdec=auto（硬件解码直接渲染到 Surface）
 * Buffer 模式: vo=gpu-next + ohcodec-copy（帧复制到 GPU 缓冲，软件渲染）
 */

#include "mpv_config.h"
#include "mpv_wrapper.h"
#include <hilog/log.h>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <mutex>

#undef LOG_DOMAIN
#define LOG_DOMAIN 0xD003D00
#undef LOG_TAG
#define LOG_TAG "mpvconfig"

// ========== 常量定义 ==========
constexpr int32_t MIN_CACHE_SIZE_MB = 64;
constexpr int32_t MAX_CACHE_SIZE_MB = 2048;
constexpr int32_t DEFAULT_SUB_FONT_SIZE = 55;
constexpr int32_t DEFAULT_SUB_POS = 100;
constexpr int32_t DEFAULT_OSD_LEVEL = 0;

// ========== 线程安全保护 ==========
static std::mutex g_config_mutex;

// 全局模式变量
static HwdecMode g_hwdec_mode = HWDEC_MODE_SURFACE;  // 默认 Surface 模式
static DecodeType g_decode_type = DECODE_TYPE_HW;
static int32_t g_cache_size_mb = 1024;
static bool g_auto_buffer_fallback = false;

// 字幕样式全局配置
static int32_t g_subtitle_font_size = DEFAULT_SUB_FONT_SIZE;
static char g_subtitle_font[128] = "Noto Sans CJK SC";
static char g_subtitle_color[32] = "#FFFFFF";
static int32_t g_subtitle_shadow = 1;
static int32_t g_subtitle_pos = DEFAULT_SUB_POS;
static int32_t g_osd_level = DEFAULT_OSD_LEVEL;
static char g_log_level[16] = "warn";

// OSD Surface 信息（Surface 模式下用于字幕叠加层）
static uint64_t g_osd_surface_id = 0;
static uint64_t g_osd_surface_width = 0;
static uint64_t g_osd_surface_height = 0;

// libmpv 导出的 OSD surface 全局变量设置函数（ohos_osd.c 中定义）
// 作为 mpv 选项系统的备用通道：VO preinit 优先读选项，选项为 0 时回退到此全局值。
extern "C" {
    void ohos_osd_set_global_surface(uint64_t surface_id, int width, int height)
        __attribute__((weak));
}

// ========== 公共接口 (线程安全) ==========

HwdecMode GetHwdecMode() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_hwdec_mode;
}

void SetHwdecMode(HwdecMode mode) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    g_hwdec_mode = mode;
    OH_LOG_INFO(LOG_APP, "[SetHwdecMode] mode=%{public}d (%{public}s)", mode,
                mode == HWDEC_MODE_BUFFER ? "buffer" : "surface");
}

DecodeType GetDecodeType() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_decode_type;
}

void SetDecodeType(DecodeType type) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    g_decode_type = type;
    OH_LOG_INFO(LOG_APP, "[SetDecodeType] type=%{public}d (%{public}s)", type,
                type == DECODE_TYPE_HW ? "hardware" : "software");
}

int32_t GetCacheSize() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_cache_size_mb;
}

void SetCacheSize(int32_t size_mb) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    if (size_mb < MIN_CACHE_SIZE_MB) size_mb = MIN_CACHE_SIZE_MB;
    if (size_mb > MAX_CACHE_SIZE_MB) size_mb = MAX_CACHE_SIZE_MB;
    g_cache_size_mb = size_mb;
    OH_LOG_INFO(LOG_APP, "[SetCacheSize] size=%{public}dMB", size_mb);
}

bool GetAutoBufferFallback() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_auto_buffer_fallback;
}

void SetAutoBufferFallback(bool fallback) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    g_auto_buffer_fallback = fallback;
    OH_LOG_INFO(LOG_APP, "[SetAutoBufferFallback] fallback=%{public}d", fallback);
}

// ========== 字幕样式管理 ==========

int32_t GetSubtitleFontSize() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_subtitle_font_size;
}

void SetSubtitleFontSize(int32_t size) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    if (size < 10) size = 10;
    if (size > 200) size = 200;
    g_subtitle_font_size = size;
    OH_LOG_INFO(LOG_APP, "[SetSubtitleFontSize] size=%{public}d", size);
}

const char* GetSubtitleFont() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_subtitle_font;
}

void SetSubtitleFont(const char* font) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    if (font && strlen(font) > 0 && strlen(font) < sizeof(g_subtitle_font)) {
        strncpy(g_subtitle_font, font, sizeof(g_subtitle_font) - 1);
        g_subtitle_font[sizeof(g_subtitle_font) - 1] = '\0';
        OH_LOG_INFO(LOG_APP, "[SetSubtitleFont] font=%{public}s", font);
    }
}

const char* GetSubtitleColor() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_subtitle_color;
}

void SetSubtitleColor(const char* color) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    if (color && strlen(color) > 0 && strlen(color) < sizeof(g_subtitle_color)) {
        strncpy(g_subtitle_color, color, sizeof(g_subtitle_color) - 1);
        g_subtitle_color[sizeof(g_subtitle_color) - 1] = '\0';
        OH_LOG_INFO(LOG_APP, "[SetSubtitleColor] color=%{public}s", color);
    }
}

int32_t GetSubtitleShadow() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_subtitle_shadow;
}

void SetSubtitleShadow(int32_t enabled) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    g_subtitle_shadow = (enabled != 0) ? 1 : 0;
    OH_LOG_INFO(LOG_APP, "[SetSubtitleShadow] enabled=%{public}d", g_subtitle_shadow);
}

int32_t GetSubtitlePos() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_subtitle_pos;
}

void SetSubtitlePos(int32_t pos) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    if (pos < 0) pos = 0;
    if (pos > 150) pos = 150;
    g_subtitle_pos = pos;
    OH_LOG_INFO(LOG_APP, "[SetSubtitlePos] pos=%{public}d", pos);
}

int32_t GetOsdLevel() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_osd_level;
}

void SetOsdLevel(int32_t level) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    if (level < 0) level = 0;
    if (level > 3) level = 3;
    g_osd_level = level;
    OH_LOG_INFO(LOG_APP, "[SetOsdLevel] level=%{public}d", level);
}

void SetLogLevel(const char* level) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    if (level && strlen(level) > 0 && strlen(level) < sizeof(g_log_level)) {
        strncpy(g_log_level, level, sizeof(g_log_level) - 1);
        g_log_level[sizeof(g_log_level) - 1] = '\0';
        OH_LOG_INFO(LOG_APP, "[SetLogLevel] level=%{public}s", level);
    }
}

// ========== OSD Surface 管理 ==========

void SetOsdSurface(uint64_t surfaceId, uint64_t width, uint64_t height) {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    g_osd_surface_id = surfaceId;
    g_osd_surface_width = width;
    g_osd_surface_height = height;
    OH_LOG_INFO(LOG_APP, "[SetOsdSurface] id=%{public}llu, size=%{public}llux%{public}llu",
                (unsigned long long)surfaceId, (unsigned long long)width, (unsigned long long)height);

    if (ohos_osd_set_global_surface) {
        ohos_osd_set_global_surface(surfaceId, (int)width, (int)height);
        OH_LOG_INFO(LOG_APP, "[SetOsdSurface] Also set libmpv global OSD surface");
    } else {
        OH_LOG_WARN(LOG_APP, "[SetOsdSurface] ohos_osd_set_global_surface not available");
    }
}

uint64_t GetOsdSurfaceId() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_osd_surface_id;
}

uint64_t GetOsdSurfaceWidth() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_osd_surface_width;
}

uint64_t GetOsdSurfaceHeight() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_osd_surface_height;
}

void ClearOsdSurface() {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    g_osd_surface_id = 0;
    g_osd_surface_width = 0;
    g_osd_surface_height = 0;
    OH_LOG_INFO(LOG_APP, "[ClearOsdSurface] OSD surface cleared");
}

// ========== NAPI 函数 ==========

napi_value SetHwdecModeNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    int32_t mode_value;
    if (napi_get_value_int32(env, args[0], &mode_value) != napi_ok) {
        return nullptr;
    }
    HwdecMode mode = (mode_value == 1) ? HWDEC_MODE_SURFACE : HWDEC_MODE_BUFFER;
    SetHwdecMode(mode);
    return nullptr;
}

napi_value GetHwdecModeNapi(napi_env env, napi_callback_info info) {
    HwdecMode mode = GetHwdecMode();
    napi_value result;
    napi_create_int32(env, (mode == HWDEC_MODE_BUFFER) ? 0 : 1, &result);
    return result;
}

napi_value SetDecodeTypeNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    int32_t type_value;
    if (napi_get_value_int32(env, args[0], &type_value) != napi_ok) {
        return nullptr;
    }
    DecodeType type = (type_value == 1) ? DECODE_TYPE_SW : DECODE_TYPE_HW;
    SetDecodeType(type);
    return nullptr;
}

napi_value GetDecodeTypeNapi(napi_env env, napi_callback_info info) {
    DecodeType type = GetDecodeType();
    napi_value result;
    napi_create_int32(env, (type == DECODE_TYPE_HW) ? 0 : 1, &result);
    return result;
}

napi_value SetCacheSizeNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    int32_t size_mb;
    if (napi_get_value_int32(env, args[0], &size_mb) != napi_ok) {
        return nullptr;
    }
    SetCacheSize(size_mb);
    return nullptr;
}

napi_value GetCacheSizeNapi(napi_env env, napi_callback_info info) {
    napi_value result;
    napi_create_int32(env, GetCacheSize(), &result);
    return result;
}

napi_value SetAutoBufferFallbackNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    bool enabled = false;
    napi_get_value_bool(env, args[0], &enabled);
    SetAutoBufferFallback(enabled);
    return nullptr;
}

napi_value GetAutoBufferFallbackNapi(napi_env env, napi_callback_info info) {
    napi_value result;
    napi_get_boolean(env, GetAutoBufferFallback(), &result);
    return result;
}

napi_value SetSubtitleFontSizeNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    int32_t size;
    napi_get_value_int32(env, args[0], &size);
    SetSubtitleFontSize(size);
    return nullptr;
}

napi_value GetSubtitleFontSizeNapi(napi_env env, napi_callback_info info) {
    napi_value result;
    napi_create_int32(env, GetSubtitleFontSize(), &result);
    return result;
}

napi_value SetSubtitleFontNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    char font[256] = {};
    size_t len = 0;
    napi_get_value_string_utf8(env, args[0], font, sizeof(font), &len);
    SetSubtitleFont(font);
    return nullptr;
}

napi_value GetSubtitleFontNapi(napi_env env, napi_callback_info info) {
    napi_value result;
    napi_create_string_utf8(env, GetSubtitleFont(), NAPI_AUTO_LENGTH, &result);
    return result;
}

napi_value SetSubtitleColorNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    char color[64] = {};
    size_t len = 0;
    napi_get_value_string_utf8(env, args[0], color, sizeof(color), &len);
    SetSubtitleColor(color);
    return nullptr;
}

napi_value GetSubtitleColorNapi(napi_env env, napi_callback_info info) {
    napi_value result;
    napi_create_string_utf8(env, GetSubtitleColor(), NAPI_AUTO_LENGTH, &result);
    return result;
}

napi_value SetSubtitleShadowNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    int32_t enabled;
    napi_get_value_int32(env, args[0], &enabled);
    SetSubtitleShadow(enabled);
    return nullptr;
}

napi_value SetSubtitlePosNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    int32_t pos;
    napi_get_value_int32(env, args[0], &pos);
    SetSubtitlePos(pos);
    return nullptr;
}

napi_value GetSubtitlePosNapi(napi_env env, napi_callback_info info) {
    napi_value result;
    napi_create_int32(env, GetSubtitlePos(), &result);
    return result;
}

napi_value SetOsdLevelNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    int32_t level;
    napi_get_value_int32(env, args[0], &level);
    SetOsdLevel(level);
    return nullptr;
}

napi_value GetOsdLevelNapi(napi_env env, napi_callback_info info) {
    napi_value result;
    napi_create_int32(env, GetOsdLevel(), &result);
    return result;
}

napi_value SetLogLevelNapi(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 1) {
        return nullptr;
    }
    size_t str_size = 0;
    napi_get_value_string_utf8(env, args[0], nullptr, 0, &str_size);
    char* level = new char[str_size + 1];
    napi_get_value_string_utf8(env, args[0], level, str_size + 1, &str_size);
    SetLogLevel(level);
    delete[] level;
    return nullptr;
}

napi_value SetOsdSurfaceNapi(napi_env env, napi_callback_info info) {
    size_t argc = 3;
    napi_value args[3];
    if (napi_get_cb_info(env, info, &argc, args, nullptr, nullptr) != napi_ok || argc < 3) {
        OH_LOG_ERROR(LOG_APP, "[SetOsdSurfaceNapi] Need 3 params: surfaceId, width, height");
        return nullptr;
    }
    size_t len = 0;
    napi_get_value_string_utf8(env, args[0], nullptr, 0, &len);
    char* sid_str = new char[len + 1];
    napi_get_value_string_utf8(env, args[0], sid_str, len + 1, &len);
    uint64_t surfaceId = strtoull(sid_str, nullptr, 10);
    delete[] sid_str;
    double w = 0, h = 0;
    napi_get_value_double(env, args[1], &w);
    napi_get_value_double(env, args[2], &h);
    SetOsdSurface(surfaceId, (uint64_t)w, (uint64_t)h);
    return nullptr;
}

// ========== MPV 配置核心逻辑 ==========

static bool SetMpvOption(mpv_handle *mpv, const char* name, const char* value) {
    if (!mpv || !name || !value) return false;
    int ret = mpv_set_option_string(mpv, name, value);
    if (ret < 0) {
        OH_LOG_WARN(LOG_APP, "[ConfigureMPV] Failed to set '%{public}s'='%{public}s': %{public}s",
                    name, value, mpv_error_string(ret));
        return false;
    }
    return true;
}

bool ConfigureMPV(mpv_handle *mpv) {
    if (!mpv) {
        OH_LOG_ERROR(LOG_APP, "[ConfigureMPV] ERROR: mpv handle is null");
        return false;
    }

    HwdecMode mode;
    DecodeType decode_type;
    int32_t cache_size_mb;
    int32_t osd_level;
    const char* log_level;
    {
        std::lock_guard<std::mutex> lock(g_config_mutex);
        mode = g_hwdec_mode;
        decode_type = g_decode_type;
        cache_size_mb = g_cache_size_mb;
        osd_level = g_osd_level;
        log_level = g_log_level;
    }

    OH_LOG_INFO(LOG_APP, "[ConfigureMPV] mode=%{public}s, decode=%{public}s",
                mode == HWDEC_MODE_BUFFER ? "BUFFER" : "SURFACE",
                decode_type == DECODE_TYPE_HW ? "HW" : "SW");

    // ========== 通用配置 ==========
    SetMpvOption(mpv, "force-window", "yes");
    SetMpvOption(mpv, "idle", "once");
    SetMpvOption(mpv, "keepaspect", "yes");

    // ========== 视频输出和硬件解码配置 ==========
    if (mode == HWDEC_MODE_BUFFER) {
        SetMpvOption(mpv, "vo", "gpu-next");
        if (decode_type == DECODE_TYPE_SW) {
            SetMpvOption(mpv, "hwdec", "no");
            OH_LOG_INFO(LOG_APP, "[ConfigureMPV] vo=gpu-next, hwdec=no (buffer SW, gpu-api=auto)");
        } else {
            SetMpvOption(mpv, "hwdec", "ohcodec-copy");
            OH_LOG_INFO(LOG_APP, "[ConfigureMPV] vo=gpu-next, hwdec=ohcodec-copy (buffer HW, gpu-api=auto)");
        }
    } else {
        // Surface 模式：ohcodec-osd + hwdec=auto
        SetMpvOption(mpv, "vo", "ohcodec-osd");
        SetMpvOption(mpv, "hwdec", "auto");
        OH_LOG_INFO(LOG_APP, "[ConfigureMPV] vo=ohcodec-osd, hwdec=auto (surface HW)");
    }

    // ========== 字幕配置 ==========
    SetMpvOption(mpv, "sub-fonts-dir", "/system/fonts");
    SetMpvOption(mpv, "sub-font", GetSubtitleFont());

    char fontSizeStr[16];
    snprintf(fontSizeStr, sizeof(fontSizeStr), "%d", GetSubtitleFontSize());
    SetMpvOption(mpv, "sub-font-size", fontSizeStr);
    SetMpvOption(mpv, "sub-color", GetSubtitleColor());

    if (GetSubtitleShadow()) {
        SetMpvOption(mpv, "sub-shadow-offset", "2");
        SetMpvOption(mpv, "sub-shadow-color", "#00000080");
    } else {
        SetMpvOption(mpv, "sub-shadow-offset", "0");
    }

    SetMpvOption(mpv, "embeddedfonts", "yes");
    SetMpvOption(mpv, "sub-ass-override", "scale");
    SetMpvOption(mpv, "sub-ass-force-margins", "yes");
    SetMpvOption(mpv, "sub-use-margins", "yes");
    SetMpvOption(mpv, "sub-auto", "fuzzy");
    SetMpvOption(mpv, "sub-visibility", "yes");
    SetMpvOption(mpv, "blend-subtitles", "no");

    char subPosStr[16];
    snprintf(subPosStr, sizeof(subPosStr), "%d", GetSubtitlePos());
    SetMpvOption(mpv, "sub-margin-y", "0");

    OH_LOG_INFO(LOG_APP, "[ConfigureMPV] Subtitle: font=%{public}s, size=%{public}d, color=%{public}s, shadow=%{public}d",
                GetSubtitleFont(), GetSubtitleFontSize(), GetSubtitleColor(), GetSubtitleShadow());

    // ========== 解码优化 ==========
    SetMpvOption(mpv, "vd-lavc-threads", "0");
    SetMpvOption(mpv, "vd-lavc-fast", "yes");
    SetMpvOption(mpv, "framedrop", "yes");
    SetMpvOption(mpv, "profile", "fast");

    // ========== 缩放算法 ==========
    SetMpvOption(mpv, "scale", "bilinear");
    SetMpvOption(mpv, "dscale", "bilinear");
    SetMpvOption(mpv, "cscale", "bilinear");

    // ========== 音视频同步 ==========
    SetMpvOption(mpv, "video-sync", "audio");
    SetMpvOption(mpv, "audio-buffer", "0.3");

    // ========== 网络配置 ==========
    SetMpvOption(mpv, "tls-verify", "no");
    SetMpvOption(mpv, "network-timeout", "60");
    SetMpvOption(mpv, "stream-lavf-o",
        "reconnect=1,reconnect_on_network_error=1,reconnect_on_http_error=4xx,5xx,reconnect_delay_max=2,reconnect_count=5,seekable=1,buffer_size=4194304");
    SetMpvOption(mpv, "stream-buffer-size", "4M");
    SetMpvOption(mpv, "demuxer-seekable-cache", "yes");

    // ========== 缓存配置 ==========
    int64_t cache_size_bytes = (int64_t)cache_size_mb * 1024 * 1024;
    int64_t back_cache_bytes = cache_size_bytes / 2;
    char cache_size_str[32];
    char back_cache_str[32];
    snprintf(cache_size_str, sizeof(cache_size_str), "%lld", (long long)cache_size_bytes);
    snprintf(back_cache_str, sizeof(back_cache_str), "%lld", (long long)back_cache_bytes);
    SetMpvOption(mpv, "cache", "yes");
    SetMpvOption(mpv, "demuxer-max-bytes", cache_size_str);
    SetMpvOption(mpv, "demuxer-max-back-bytes", back_cache_str);
    SetMpvOption(mpv, "demuxer-readahead-secs", "2");

    OH_LOG_INFO(LOG_APP, "[ConfigureMPV] Cache: forward=%{public}dMB, backward=%{public}dMB",
                cache_size_mb, cache_size_mb / 2);

    // ========== OSD配置 ==========
    char osd_level_str[16];
    snprintf(osd_level_str, sizeof(osd_level_str), "%d", osd_level);
    SetMpvOption(mpv, "osd-level", osd_level_str);
    SetMpvOption(mpv, "osd-duration", "3000");
    SetMpvOption(mpv, "osd-font-size", "40");
    SetMpvOption(mpv, "osd-color", "#FFFFFF");
    SetMpvOption(mpv, "osd-border-color", "#000000");
    SetMpvOption(mpv, "osd-border-size", "2");

    // ========== 日志配置 ==========
    mpv_request_log_messages(mpv, log_level);
    OH_LOG_INFO(LOG_APP, "[ConfigureMPV] Log level: %{public}s", log_level);

    OH_LOG_INFO(LOG_APP, "[ConfigureMPV] Configuration completed");
    return true;
}
