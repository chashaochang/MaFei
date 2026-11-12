#pragma once

namespace {
napi_value PlayerWindowNumber(napi_env env, int64_t value) {
    napi_value result = nullptr;
    napi_create_int64(env, value, &result);
    return result;
}

napi_value PlayerWindowUndefined(napi_env env) {
    napi_value result = nullptr;
    napi_get_undefined(env, &result);
    return result;
}

napi_value CreatePlayerController(napi_env env, napi_callback_info) {
    return reinterpret_cast<napi_value>(PlayerArkUIViewController(env));
}

napi_value PlayerWindowRequestId(napi_env env, napi_callback_info) {
    return PlayerWindowNumber(env, GetOhosPlayerWindowRequestId());
}

napi_value PlayerWindowRevision(napi_env env, napi_callback_info) {
    return PlayerWindowNumber(env, GetOhosPlayerWindowRevision());
}

napi_value PlayerWindowFullscreenRevision(napi_env env, napi_callback_info) {
    return PlayerWindowNumber(env, GetOhosPlayerWindowFullscreenRevision());
}

napi_value PlayerWindowTitle(napi_env env, napi_callback_info) {
    return reinterpret_cast<napi_value>(GetOhosPlayerWindowTitle(env));
}

napi_value PlayerWindowFullscreen(napi_env env, napi_callback_info) {
    napi_value result = nullptr;
    napi_get_boolean(env, IsOhosPlayerWindowFullscreenRequested(), &result);
    return result;
}

napi_value EnablePlayerWindow(napi_env env, napi_callback_info info) {
    size_t count = 1;
    napi_value argument;
    bool enabled = false;
    if (napi_get_cb_info(env, info, &count, &argument, nullptr, nullptr) != napi_ok || count != 1 ||
        napi_get_value_bool(env, argument, &enabled) != napi_ok) {
        napi_throw_type_error(env, nullptr, "One player window capability value is required");
        return nullptr;
    }
    SetOhosPlayerWindowEnabled(enabled);
    return PlayerWindowUndefined(env);
}

napi_value CompletePlayerWindow(napi_env env, napi_callback_info info, bool failed) {
    size_t count = 1;
    napi_value argument;
    int64_t id = 0;
    if (napi_get_cb_info(env, info, &count, &argument, nullptr, nullptr) != napi_ok || count != 1 ||
        napi_get_value_int64(env, argument, &id) != napi_ok || id <= 0) {
        napi_throw_type_error(env, nullptr, "One positive player window request ID is required");
        return nullptr;
    }
    if (failed) FailOhosPlayerWindow(id);
    else CloseOhosPlayerWindow(id);
    return PlayerWindowUndefined(env);
}

napi_value ClosePlayerWindow(napi_env env, napi_callback_info info) {
    return CompletePlayerWindow(env, info, false);
}

napi_value FailPlayerWindow(napi_env env, napi_callback_info info) {
    return CompletePlayerWindow(env, info, true);
}

napi_value PlayerWindowBack(napi_env env, napi_callback_info) {
    napi_value result = nullptr;
    napi_get_boolean(env, HandleOhosPlayerWindowBack(), &result);
    return result;
}

napi_value UpdatePlayerWindowMetrics(napi_env env, napi_callback_info info) {
    size_t count = 7;
    napi_value arguments[7];
    double insets[6] = {};
    bool fullscreen = false;
    if (napi_get_cb_info(env, info, &count, arguments, nullptr, nullptr) != napi_ok || count != 7) {
        napi_throw_type_error(env, nullptr, "Player window insets, chrome and fullscreen state are required");
        return nullptr;
    }
    for (size_t index = 0; index < 6; index++) {
        if (napi_get_value_double(env, arguments[index], &insets[index]) != napi_ok) {
            napi_throw_type_error(env, nullptr, "Invalid player window inset");
            return nullptr;
        }
    }
    if (napi_get_value_bool(env, arguments[6], &fullscreen) != napi_ok) {
        napi_throw_type_error(env, nullptr, "Invalid player window fullscreen state");
        return nullptr;
    }
    UpdateOhosPlayerWindowMetrics(insets[0], insets[1], insets[2], insets[3], insets[4], insets[5], fullscreen);
    return PlayerWindowUndefined(env);
}

void RegisterPlayerWindowBridge(napi_env env, napi_value exports) {
    napi_property_descriptor descriptors[] = {
        {"PlayerArkUIViewController", nullptr, CreatePlayerController, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"SetOhosPlayerWindowEnabled", nullptr, EnablePlayerWindow, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"GetOhosPlayerWindowRequestId", nullptr, PlayerWindowRequestId, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"GetOhosPlayerWindowRevision", nullptr, PlayerWindowRevision, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"GetOhosPlayerWindowTitle", nullptr, PlayerWindowTitle, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"GetOhosPlayerWindowFullscreenRevision", nullptr, PlayerWindowFullscreenRevision, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"IsOhosPlayerWindowFullscreenRequested", nullptr, PlayerWindowFullscreen, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"CloseOhosPlayerWindow", nullptr, ClosePlayerWindow, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"FailOhosPlayerWindow", nullptr, FailPlayerWindow, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"HandleOhosPlayerWindowBack", nullptr, PlayerWindowBack, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"UpdateOhosPlayerWindowMetrics", nullptr, UpdatePlayerWindowMetrics, nullptr, nullptr, nullptr, napi_default, nullptr},
    };
    napi_define_properties(env, exports, sizeof(descriptors) / sizeof(descriptors[0]), descriptors);
}
}
