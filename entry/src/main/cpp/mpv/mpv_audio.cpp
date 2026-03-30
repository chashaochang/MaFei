#include "mpv_audio.h"
#include <mpv/client.h>
#include <hilog/log.h>
#include <cstring>

#undef LOG_DOMAIN
#define LOG_DOMAIN 0xD003D00
#undef LOG_TAG
#define LOG_TAG "mpvnative"

// 外部全局 MPV 句柄（在 mpv_wrapper.cpp 中定义）
extern mpv_handle *global_mpv;

// Get audio track information
napi_value GetAudioTracks(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [GetAudioTracks] called");
    
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetAudioTracks] ERROR: mpv not created");
        return nullptr;
    }
    
    napi_value result;
    napi_create_array(env, &result);
    
    // Query track-list property
    mpv_node tracks;
    int ret = mpv_get_property(global_mpv, "track-list", MPV_FORMAT_NODE, &tracks);
    
    if (ret != 0) {
        OH_LOG_ERROR(LOG_APP, "[GetAudioTracks] Failed to get track list: ret=%{public}d", ret);
        return result;
    }
    
    if (tracks.format != MPV_FORMAT_NODE_ARRAY) {
        OH_LOG_ERROR(LOG_APP, "[GetAudioTracks] track-list is not array");
        mpv_free_node_contents(&tracks);
        return result;
    }
    
    uint32_t array_index = 0;
    
    // Iterate through all tracks
    for (int i = 0; i < tracks.u.list->num; i++) {
        mpv_node *track_node = &tracks.u.list->values[i];
        
        if (track_node->format != MPV_FORMAT_NODE_MAP) {
            continue;
        }
        
        // Check if this is an audio track
        mpv_node_list *map = track_node->u.list;
        const char *track_type = nullptr;
        
        for (int j = 0; j < map->num; j++) {
            if (strcmp(map->keys[j], "type") == 0 && map->values[j].format == MPV_FORMAT_STRING) {
                track_type = map->values[j].u.string;
                break;
            }
        }
        
        if (!track_type || strcmp(track_type, "audio") != 0) {
            continue;  // Skip non-audio tracks
        }
        
        // Create audio track object
        napi_value audio_obj;
        napi_create_object(env, &audio_obj);
        
        // Extract track information
        int track_id = -1;
        const char *title = nullptr;
        const char *lang = nullptr;
        const char *codec = nullptr;
        bool external = false;
        int64_t channels = 0;
        
        for (int j = 0; j < map->num; j++) {
            const char *key = map->keys[j];
            mpv_node *val = &map->values[j];
            
            if (strcmp(key, "id") == 0 && val->format == MPV_FORMAT_INT64) {
                track_id = (int)val->u.int64;
            } else if (strcmp(key, "title") == 0 && val->format == MPV_FORMAT_STRING) {
                title = val->u.string;
            } else if (strcmp(key, "lang") == 0 && val->format == MPV_FORMAT_STRING) {
                lang = val->u.string;
            } else if (strcmp(key, "codec") == 0 && val->format == MPV_FORMAT_STRING) {
                codec = val->u.string;
            } else if (strcmp(key, "external") == 0 && val->format == MPV_FORMAT_FLAG) {
                external = (bool)val->u.flag;
            } else if (strcmp(key, "demux-channel-count") == 0 && val->format == MPV_FORMAT_INT64) {
                channels = val->u.int64;
            }
        }
        
        // Set object properties
        napi_value id_val, title_val, lang_val, codec_val, external_val, channels_val;
        napi_create_int32(env, track_id, &id_val);
        napi_create_string_utf8(env, title ? title : "", NAPI_AUTO_LENGTH, &title_val);
        napi_create_string_utf8(env, lang ? lang : "unknown", NAPI_AUTO_LENGTH, &lang_val);
        napi_create_string_utf8(env, codec ? codec : "", NAPI_AUTO_LENGTH, &codec_val);
        napi_get_boolean(env, external, &external_val);
        napi_create_int32(env, (int32_t)channels, &channels_val);
        
        napi_set_named_property(env, audio_obj, "id", id_val);
        napi_set_named_property(env, audio_obj, "title", title_val);
        napi_set_named_property(env, audio_obj, "lang", lang_val);
        napi_set_named_property(env, audio_obj, "codec", codec_val);
        napi_set_named_property(env, audio_obj, "external", external_val);
        napi_set_named_property(env, audio_obj, "channels", channels_val);
        
        // Add to result array
        napi_set_element(env, result, array_index++, audio_obj);
        
        OH_LOG_INFO(LOG_APP, "[GetAudioTracks] Found audio: id=%{public}d, lang=%{public}s, codec=%{public}s, channels=%{public}lld", 
            track_id, lang ? lang : "unknown", codec ? codec : "unknown", (long long)channels);
    }
    
    mpv_free_node_contents(&tracks);
    OH_LOG_INFO(LOG_APP, "[GetAudioTracks] Total audio tracks: %{public}d", array_index);
    
    return result;
}

// Select audio track by ID
napi_value SelectAudio(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [SelectAudio] called");
    
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    
    if (argc < 2) {
        OH_LOG_ERROR(LOG_APP, "[SelectAudio] ERROR: missing parameters");
        return nullptr;
    }
    
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[SelectAudio] ERROR: mpv not created");
        return nullptr;
    }
    
    int32_t track_id = 0;
    napi_get_value_int32(env, args[1], &track_id);
    
    OH_LOG_INFO(LOG_APP, "[SelectAudio] Selecting audio track: %{public}d", track_id);
    
    // Set aid property to select audio track
    int64_t track_id_64 = (int64_t)track_id;
    int ret = mpv_set_property(global_mpv, "aid", MPV_FORMAT_INT64, &track_id_64);
    
    if (ret < 0) {
        OH_LOG_ERROR(LOG_APP, "[SelectAudio] Failed to set aid: %{public}s", mpv_error_string(ret));
    } else {
        OH_LOG_INFO(LOG_APP, "[SelectAudio] Audio track selected successfully");
    }
    
    return nullptr;
}

// Get current audio track ID
napi_value GetCurrentAudioTrack(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [GetCurrentAudioTrack] called");
    
    size_t argc = 1;
    napi_value args[1];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetCurrentAudioTrack] ERROR: mpv not created");
        napi_value result;
        napi_create_int32(env, -1, &result);
        return result;
    }
    
    int64_t current_aid = -1;
    int ret = mpv_get_property(global_mpv, "aid", MPV_FORMAT_INT64, &current_aid);
    
    if (ret < 0) {
        OH_LOG_WARN(LOG_APP, "[GetCurrentAudioTrack] Failed to get aid: %{public}s", mpv_error_string(ret));
        current_aid = -1;
    } else {
        OH_LOG_INFO(LOG_APP, "[GetCurrentAudioTrack] Current audio track: %{public}ld", current_aid);
    }
    
    napi_value result;
    napi_create_int32(env, (int32_t)current_aid, &result);
    return result;
}
