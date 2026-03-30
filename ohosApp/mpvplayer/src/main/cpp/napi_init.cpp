#include <napi/native_api.h>
#include "ace/xcomponent/native_interface_xcomponent.h"
#include "mpv/mpv_wrapper.h"
#include "mpv/mpv_subtitle.h"
#include "mpv/mpv_audio.h"
#include "mpv/mpv_video_info.h"
#include "mpv/mpv_playback.h"
#include "mpv/mpv_surface.h"
#include "mpv/mpv_loader.h"
#include "mpv/mpv_network.h"
#include "mpv/mpv_config.h"

namespace {
    [[nodiscard]] napi_value Invoke(napi_env env, napi_value exports) {
        napi_value xComponentJS;
        napi_status const check = napi_get_named_property(env, exports, OH_NATIVE_XCOMPONENT_OBJ, &xComponentJS);

        if (check == napi_ok) {
            OH_NativeXComponent *xComponent;
            if (napi_unwrap(env, xComponentJS, reinterpret_cast<void **>(&xComponent)) == napi_ok) {
                static OH_NativeXComponent_Callback callbacks{
                    .OnSurfaceCreated = OnSurfaceCreated,
                    .OnSurfaceChanged = OnSurfaceChanged,
                    .OnSurfaceDestroyed = OnSurfaceDestroyed,
                    .DispatchTouchEvent = nullptr
                };

                OH_NativeXComponent_RegisterCallback(xComponent, &callbacks);
                return exports;
            }
        }

        napi_property_descriptor desc[] = {
            {"create", nullptr, Create, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"isInitialized", nullptr, IsInitialized, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"isDestroying", nullptr, IsDestroying, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"loadVideo", nullptr, LoadVideo, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"destroy", nullptr, Destroy, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"reset", nullptr, Reset, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getSubtitleTracks", nullptr, GetSubtitleTracks, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"selectSubtitle", nullptr, SelectSubtitle, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setSubtitleStyle", nullptr, SetSubtitleStyle, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getAudioTracks", nullptr, GetAudioTracks, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"selectAudio", nullptr, SelectAudio, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getCurrentAudioTrack", nullptr, GetCurrentAudioTrack, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getCurrentSubtitleTrack", nullptr, GetCurrentSubtitleTrack, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"seek", nullptr, Seek, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"pause", nullptr, Pause, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"play", nullptr, Play, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setSpeed", nullptr, SetSpeed, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getCurrentPosition", nullptr, GetCurrentPosition, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getDuration", nullptr, GetDuration, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getCacheDuration", nullptr, GetCacheDuration, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getHardwareDecoder", nullptr, GetHardwareDecoder, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getVideoWidth", nullptr, GetVideoWidth, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getVideoHeight", nullptr, GetVideoHeight, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setKeepAspect", nullptr, SetKeepAspect, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"command", nullptr, Command, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getNetworkSpeed", nullptr, GetNetworkSpeed, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setHwdecMode", nullptr, SetHwdecModeNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getHwdecMode", nullptr, GetHwdecModeNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setDecodeType", nullptr, SetDecodeTypeNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getDecodeType", nullptr, GetDecodeTypeNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setCacheSize", nullptr, SetCacheSizeNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getCacheSize", nullptr, GetCacheSizeNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOsdSurface", nullptr, SetOsdSurfaceNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setSubtitleFontSize", nullptr, SetSubtitleFontSizeNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getSubtitleFontSize", nullptr, GetSubtitleFontSizeNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setSubtitleFont", nullptr, SetSubtitleFontNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getSubtitleFont", nullptr, GetSubtitleFontNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setSubtitleColor", nullptr, SetSubtitleColorNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getSubtitleColor", nullptr, GetSubtitleColorNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setSubtitleShadow", nullptr, SetSubtitleShadowNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setSubtitlePos", nullptr, SetSubtitlePosNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getSubtitlePos", nullptr, GetSubtitlePosNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOsdLevel", nullptr, SetOsdLevelNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getOsdLevel", nullptr, GetOsdLevelNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setLogLevel", nullptr, SetLogLevelNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setAutoBufferFallback", nullptr, SetAutoBufferFallbackNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"getAutoBufferFallback", nullptr, GetAutoBufferFallbackNapi, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnInitializedCallback", nullptr, SetOnInitializedCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnTrackListChangedCallback", nullptr, SetOnTrackListChangedCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnDurationChangedCallback", nullptr, SetOnDurationChangedCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnEndFileCallback", nullptr, SetOnEndFileCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnHwdecChangedCallback", nullptr, SetOnHwdecChangedCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnPausedForCacheCallback", nullptr, SetOnPausedForCacheCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnLoadingCallback", nullptr, SetOnLoadingCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnStartFileCallback", nullptr, SetOnStartFileCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnFileLoadedCallback", nullptr, SetOnFileLoadedCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
            {"setOnLogMessageCallback", nullptr, SetOnLogMessageCallback, nullptr, nullptr, nullptr, napi_default, nullptr},
        };

        napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);
        return exports;
    }

    __attribute__((constructor)) void RegisterMpvNativeModule() noexcept {
        napi_module appModule{
            .nm_version = 1,
            .nm_flags = 0U,
            .nm_filename = nullptr,
            .nm_register_func = &Invoke,
            .nm_modname = "libmpvnative",
            .nm_priv = nullptr,
            .reserved = {}
        };

        napi_module_register(&appModule);
    }
}
