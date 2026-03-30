#include "mpv_subtitle.h"
#include <mpv/client.h>
#include <hilog/log.h>
#include <cstring>
#include <string>
#include <cstdio>

#undef LOG_DOMAIN
#define LOG_DOMAIN 0xD003D00
#undef LOG_TAG
#define LOG_TAG "mpvnative"

// 外部全局 MPV 句柄（在 mpv_wrapper.cpp 中定义）
extern mpv_handle *global_mpv;

// Get subtitle track information
napi_value GetSubtitleTracks(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [GetSubtitleTracks] called");
    
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetSubtitleTracks] ERROR: mpv not created");
        return nullptr;
    }
    
    napi_value result;
    napi_create_array(env, &result);
    
    // Query subtitle-tracks property (array of subtitle tracks)
    mpv_node tracks;
    int ret = mpv_get_property(global_mpv, "track-list", MPV_FORMAT_NODE, &tracks);
    
    if (ret != 0) {
        OH_LOG_ERROR(LOG_APP, "[GetSubtitleTracks] Failed to get track list: %{public}s", mpv_error_string(ret));
        return result;  // Return empty array
    }
    
    if (tracks.format != MPV_FORMAT_NODE_ARRAY) {
        mpv_free_node_contents(&tracks);
        return result;
    }
    
    uint32_t array_index = 0;
    
    // Iterate through all tracks
    for (int i = 0; i < tracks.u.list->num; i++) {
        mpv_node *track_node = &tracks.u.list->values[i];
        
        if (track_node->format != MPV_FORMAT_NODE_MAP) {
            OH_LOG_WARN(LOG_APP, "[GetSubtitleTracks] Track %{public}d is not a map, skipping", i);
            continue;
        }
        
        // Check if this is a subtitle track
        mpv_node_list *map = track_node->u.list;
        const char *track_type = nullptr;
        
        for (int j = 0; j < map->num; j++) {
            if (strcmp(map->keys[j], "type") == 0 && map->values[j].format == MPV_FORMAT_STRING) {
                track_type = map->values[j].u.string;
                break;
            }
        }
        
        if (!track_type || strcmp(track_type, "sub") != 0) {
            continue;  // Skip non-subtitle tracks
        }
        
        // Create subtitle track object
        napi_value subtitle_obj;
        napi_create_object(env, &subtitle_obj);
        
        // Extract track information
        int track_id = -1;
        const char *title = nullptr;
        const char *lang = nullptr;
        bool external = false;
        
        for (int j = 0; j < map->num; j++) {
            const char *key = map->keys[j];
            mpv_node *val = &map->values[j];
            
            if (strcmp(key, "id") == 0 && val->format == MPV_FORMAT_INT64) {
                track_id = (int)val->u.int64;
            } else if (strcmp(key, "title") == 0 && val->format == MPV_FORMAT_STRING) {
                title = val->u.string;
            } else if (strcmp(key, "lang") == 0 && val->format == MPV_FORMAT_STRING) {
                lang = val->u.string;
            } else if (strcmp(key, "external") == 0 && val->format == MPV_FORMAT_FLAG) {
                external = (bool)val->u.flag;
            }
        }
        
        // Set object properties
        napi_value id_val, title_val, lang_val, external_val;
        napi_create_int32(env, track_id, &id_val);
        napi_create_string_utf8(env, title ? title : "", NAPI_AUTO_LENGTH, &title_val);
        napi_create_string_utf8(env, lang ? lang : "unknown", NAPI_AUTO_LENGTH, &lang_val);
        napi_get_boolean(env, external, &external_val);
        
        napi_set_named_property(env, subtitle_obj, "id", id_val);
        napi_set_named_property(env, subtitle_obj, "title", title_val);
        napi_set_named_property(env, subtitle_obj, "lang", lang_val);
        napi_set_named_property(env, subtitle_obj, "external", external_val);
        
        // Add to result array
        napi_set_element(env, result, array_index++, subtitle_obj);
        
        OH_LOG_INFO(LOG_APP, "[GetSubtitleTracks] Found subtitle: id=%{public}d, lang=%{public}s, title=%{public}s", 
            track_id, lang ? lang : "unknown", title ? title : "");
    }
    
    mpv_free_node_contents(&tracks);
    OH_LOG_INFO(LOG_APP, "[GetSubtitleTracks] Total subtitles: %{public}d", array_index);
    
    return result;
}

// Select subtitle by track ID
napi_value SelectSubtitle(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [SelectSubtitle] called");
    
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    
    if (argc < 2) {
        OH_LOG_ERROR(LOG_APP, "[SelectSubtitle] ERROR: missing parameters");
        return nullptr;
    }
    
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[SelectSubtitle] ERROR: mpv not created");
        return nullptr;
    }
    
    int32_t track_id = 0;
    napi_get_value_int32(env, args[1], &track_id);
    
    OH_LOG_INFO(LOG_APP, "[SelectSubtitle] Received track_id=%{public}d", track_id);
    
    // Set sid property to select subtitle track
    // Value: "no" (disable), "auto", or track id
    if (track_id < 0) {
        // Disable subtitles
        mpv_set_property_string(global_mpv, "sid", "no");
        OH_LOG_INFO(LOG_APP, "[SelectSubtitle] Subtitles disabled");
    } else {
        // Enable subtitle visibility first
        mpv_set_property_string(global_mpv, "sub-visibility", "yes");
        
        // Set the subtitle track ID
        int64_t track_id_64 = (int64_t)track_id;
        int ret = mpv_set_property(global_mpv, "sid", MPV_FORMAT_INT64, &track_id_64);
        
        if (ret < 0) {
            OH_LOG_ERROR(LOG_APP, "[SelectSubtitle] Failed to set sid: %{public}s", mpv_error_string(ret));
        } else {
            OH_LOG_INFO(LOG_APP, "[SelectSubtitle] Set sid=%{public}d OK", track_id);
        }
    }
    
    return nullptr;
}

// Set subtitle properties (size, color, position, etc)
napi_value SetSubtitleStyle(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [SetSubtitleStyle] called");
    
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    
    if (argc < 2 || !global_mpv) {
        return nullptr;
    }
    
    // args[1] should be an object with subtitle style properties
    // Example: { fontSize: 50, color: "#FFFFFF", position: "bottom" }
    
    napi_valuetype type;
    napi_typeof(env, args[1], &type);
    
    if (type != napi_object) {
        return nullptr;
    }
    
    // Helper lambda to get string property
    auto get_string_prop = [env, args](const char *key, const char *default_val) -> std::string {
        napi_value val;
        napi_status status = napi_get_named_property(env, args[1], key, &val);
        if (status != napi_ok) {
            return std::string(default_val);
        }
        
        char buf[256];
        size_t len;
        if (napi_get_value_string_utf8(env, val, buf, sizeof(buf), &len) != napi_ok) {
            return std::string(default_val);
        }
        return std::string(buf, len);
    };
    
    // Helper lambda to get number property
    auto get_number_prop = [env, args](const char *key, int default_val) -> int {
        napi_value val;
        napi_status status = napi_get_named_property(env, args[1], key, &val);
        if (status != napi_ok) {
            return default_val;
        }
        
        int result;
        if (napi_get_value_int32(env, val, &result) != napi_ok) {
            return default_val;
        }
        return result;
    };
    
    // Get properties
    int font_size = get_number_prop("fontSize", 50);
    std::string color = get_string_prop("color", "#FFFFFF");
    
    // Set MPV subtitle properties
    char fs_str[32];
    snprintf(fs_str, sizeof(fs_str), "%d", font_size);
    mpv_set_property_string(global_mpv, "sub-font-size", fs_str);
    
    // Color format: MPV uses BGR hex format
    // Convert #RRGGBB to BGR format if needed
    mpv_set_property_string(global_mpv, "sub-color", color.c_str());
    
    OH_LOG_INFO(LOG_APP, "[SetSubtitleStyle] fontSize=%{public}d, color=%{public}s", font_size, color.c_str());
    
    return nullptr;
}

// Get current subtitle track ID
napi_value GetCurrentSubtitleTrack(napi_env env, napi_callback_info info) {
    OH_LOG_INFO(LOG_APP, ">>> [GetCurrentSubtitleTrack] called");
    
    size_t argc = 1;
    napi_value args[1];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    
    if (!global_mpv) {
        OH_LOG_ERROR(LOG_APP, "[GetCurrentSubtitleTrack] ERROR: mpv not created");
        napi_value result;
        napi_create_int32(env, -1, &result);
        return result;
    }
    
    int64_t current_sid = -1;
    int ret = mpv_get_property(global_mpv, "sid", MPV_FORMAT_INT64, &current_sid);
    
    if (ret < 0) {
        OH_LOG_WARN(LOG_APP, "[GetCurrentSubtitleTrack] Failed to get sid: %{public}s", mpv_error_string(ret));
        current_sid = -1;
    } else {
        OH_LOG_INFO(LOG_APP, "[GetCurrentSubtitleTrack] Current subtitle track: %{public}ld", current_sid);
    }
    
    napi_value result;
    napi_create_int32(env, (int32_t)current_sid, &result);
    return result;
}
