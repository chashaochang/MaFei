#include "libkn_api.h"
#include "hilog/log.h"
#include "napi/native_api.h"
#include <vector>

static napi_value CreateMainArkUIViewController(napi_env env, napi_callback_info info) {
    size_t count = 4;
    napi_value arguments[4];
    size_t directoryLength = 0;
    size_t bundleLength = 0;
    size_t snapshotLength = 0;
    size_t cmpSettingsLength = 0;
    if (napi_get_cb_info(env, info, &count, arguments, nullptr, nullptr) != napi_ok || count != 4 ||
        napi_get_value_string_utf8(env, arguments[0], nullptr, 0, &directoryLength) != napi_ok ||
        directoryLength == 0 || directoryLength > 4096 ||
        napi_get_value_string_utf8(env, arguments[1], nullptr, 0, &bundleLength) != napi_ok ||
        bundleLength == 0 || bundleLength > 255 ||
        napi_get_value_string_utf8(env, arguments[2], nullptr, 0, &snapshotLength) != napi_ok ||
        snapshotLength == 0 || snapshotLength > 1024 * 1024 ||
        napi_get_value_string_utf8(env, arguments[3], nullptr, 0, &cmpSettingsLength) != napi_ok ||
        cmpSettingsLength == 0 || cmpSettingsLength > 4 * 1024 * 1024) {
        napi_throw_type_error(env, nullptr, "A private files directory, bundle name and compatibility snapshots are required");
        return nullptr;
    }
    std::vector<char> directory(directoryLength + 1);
    std::vector<char> bundle(bundleLength + 1);
    std::vector<char> snapshot(snapshotLength + 1);
    std::vector<char> cmpSettings(cmpSettingsLength + 1);
    size_t copiedDirectory = 0;
    size_t copiedBundle = 0;
    size_t copiedSnapshot = 0;
    size_t copiedCmpSettings = 0;
    if (napi_get_value_string_utf8(env, arguments[0], directory.data(), directory.size(), &copiedDirectory) != napi_ok ||
        copiedDirectory != directoryLength || directory[0] != '/' ||
        napi_get_value_string_utf8(env, arguments[1], bundle.data(), bundle.size(), &copiedBundle) != napi_ok ||
        copiedBundle != bundleLength ||
        napi_get_value_string_utf8(env, arguments[2], snapshot.data(), snapshot.size(), &copiedSnapshot) != napi_ok ||
        copiedSnapshot != snapshotLength ||
        napi_get_value_string_utf8(env, arguments[3], cmpSettings.data(), cmpSettings.size(), &copiedCmpSettings) != napi_ok ||
        copiedCmpSettings != cmpSettingsLength) {
        napi_throw_type_error(env, nullptr, "Invalid private files directory, bundle name or compatibility snapshot");
        return nullptr;
    }
    for (size_t index = 0; index < copiedDirectory; ++index) {
        if (directory[index] == '\0') {
            napi_throw_type_error(env, nullptr, "Invalid private files directory");
            return nullptr;
        }
    }
    for (size_t index = 0; index < copiedBundle; ++index) {
        if (bundle[index] == '\0') {
            napi_throw_type_error(env, nullptr, "Invalid bundle name");
            return nullptr;
        }
    }
    for (size_t index = 0; index < copiedSnapshot; ++index) {
        if (snapshot[index] == '\0') {
            napi_throw_type_error(env, nullptr, "Invalid legacy snapshot");
            return nullptr;
        }
    }
    for (size_t index = 0; index < copiedCmpSettings; ++index) {
        if (cmpSettings[index] == '\0') {
            napi_throw_type_error(env, nullptr, "Invalid legacy CMP settings snapshot");
            return nullptr;
        }
    }
    // Kotlin copies the string during this call; no temporary pointer or
    // N-API handle escapes to a background persistence worker.
    return reinterpret_cast<napi_value>(
        MainArkUIViewController(env, directory.data(), bundle.data(), snapshot.data(), cmpSettings.data())
    );
}

static napi_value UpdateWindowInsets(napi_env env, napi_callback_info info) {
    size_t count = 4;
    napi_value arguments[4];
    double left = 0.0;
    double top = 0.0;
    double right = 0.0;
    double bottom = 0.0;
    if (napi_get_cb_info(env, info, &count, arguments, nullptr, nullptr) != napi_ok || count != 4 ||
        napi_get_value_double(env, arguments[0], &left) != napi_ok ||
        napi_get_value_double(env, arguments[1], &top) != napi_ok ||
        napi_get_value_double(env, arguments[2], &right) != napi_ok ||
        napi_get_value_double(env, arguments[3], &bottom) != napi_ok) {
        napi_throw_type_error(env, nullptr, "Four safe-area values are required");
        return nullptr;
    }
    UpdateOhosWindowInsets(left, top, right, bottom);
    napi_value result = nullptr;
    napi_get_undefined(env, &result);
    return result;
}

static napi_value UpdateWindowMetrics(napi_env env, napi_callback_info info) {
    size_t count = 4;
    napi_value arguments[4];
    double width = 0.0;
    double height = 0.0;
    bool desktopHost = false;
    bool windowedHost = false;
    if (napi_get_cb_info(env, info, &count, arguments, nullptr, nullptr) != napi_ok || count != 4 ||
        napi_get_value_double(env, arguments[0], &width) != napi_ok ||
        napi_get_value_double(env, arguments[1], &height) != napi_ok ||
        napi_get_value_bool(env, arguments[2], &desktopHost) != napi_ok ||
        napi_get_value_bool(env, arguments[3], &windowedHost) != napi_ok) {
        napi_throw_type_error(env, nullptr, "Window dimensions, desktop host state and windowed host state are required");
        return nullptr;
    }
    UpdateOhosWindowMetrics(width, height, desktopHost, windowedHost);
    napi_value result = nullptr;
    napi_get_undefined(env, &result);
    return result;
}

static napi_value UpdateWindowChrome(napi_env env, napi_callback_info info) {
    size_t count = 3;
    napi_value arguments[3];
    double endInset = 0.0;
    double topInset = 0.0;
    bool fullscreen = false;
    if (napi_get_cb_info(env, info, &count, arguments, nullptr, nullptr) != napi_ok || count != 3 ||
        napi_get_value_double(env, arguments[0], &endInset) != napi_ok ||
        napi_get_value_double(env, arguments[1], &topInset) != napi_ok ||
        napi_get_value_bool(env, arguments[2], &fullscreen) != napi_ok) {
        napi_throw_type_error(env, nullptr, "Window chrome inset and fullscreen state are required");
        return nullptr;
    }
    UpdateOhosWindowChrome(endInset, topInset, fullscreen);
    napi_value result = nullptr;
    napi_get_undefined(env, &result);
    return result;
}

static napi_value UpdatePlayerState(napi_env env, napi_callback_info info) {
    size_t count = 1;
    napi_value arguments[1];
    size_t payloadLength = 0;
    if (napi_get_cb_info(env, info, &count, arguments, nullptr, nullptr) != napi_ok || count != 1 ||
        napi_get_value_string_utf8(env, arguments[0], nullptr, 0, &payloadLength) != napi_ok ||
        payloadLength == 0 || payloadLength > 1024 * 1024) {
        napi_throw_type_error(env, nullptr, "One player state JSON string is required");
        return nullptr;
    }
    std::vector<char> payload(payloadLength + 1);
    size_t copiedPayload = 0;
    if (napi_get_value_string_utf8(
            env,
            arguments[0],
            payload.data(),
            payload.size(),
            &copiedPayload
        ) != napi_ok || copiedPayload != payloadLength) {
        napi_throw_type_error(env, nullptr, "Invalid player state JSON string");
        return nullptr;
    }
    const bool accepted = UpdateOhosPlayerState(payload.data());
    if (!accepted) {
        OH_LOG_Print(
            LOG_APP,
            LOG_WARN,
            0xD003900,
            "MaFeiPlayerBridge",
            "Rejected stale or invalid player event"
        );
    }
    napi_value result = nullptr;
    napi_get_boolean(env, accepted, &result);
    return result;
}

static napi_value GetPlayerScreenVisible(napi_env env, napi_callback_info info) {
    size_t count = 0;
    if (napi_get_cb_info(env, info, &count, nullptr, nullptr, nullptr) != napi_ok || count != 0) {
        napi_throw_type_error(env, nullptr, "No arguments are accepted");
        return nullptr;
    }
    napi_value result = nullptr;
    napi_get_boolean(env, IsOhosPlayerScreenVisible(), &result);
    return result;
}

static napi_value GetPlayerFullscreenRequested(napi_env env, napi_callback_info info) {
    size_t count = 0;
    if (napi_get_cb_info(env, info, &count, nullptr, nullptr, nullptr) != napi_ok || count != 0) {
        napi_throw_type_error(env, nullptr, "No arguments are accepted");
        return nullptr;
    }
    napi_value result = nullptr;
    napi_get_boolean(env, IsOhosPlayerFullscreenRequested(), &result);
    return result;
}

static napi_value GetPlayerOrientationRevision(napi_env env, napi_callback_info info) {
    size_t count = 0;
    if (napi_get_cb_info(env, info, &count, nullptr, nullptr, nullptr) != napi_ok || count != 0) {
        napi_throw_type_error(env, nullptr, "No arguments are accepted");
        return nullptr;
    }
    napi_value result = nullptr;
    napi_create_int64(env, GetOhosPlayerOrientationRevision(), &result);
    return result;
}

static napi_value HandleComposeBack(napi_env env, napi_callback_info info) {
    size_t count = 0;
    if (napi_get_cb_info(env, info, &count, nullptr, nullptr, nullptr) != napi_ok || count != 0) {
        napi_throw_type_error(env, nullptr, "No arguments are accepted");
        return nullptr;
    }
    napi_value result = nullptr;
    napi_get_boolean(env, HandleOhosComposeBack(), &result);
    return result;
}

static napi_value HandlePlayerBack(napi_env env, napi_callback_info info) {
    size_t count = 0;
    if (napi_get_cb_info(env, info, &count, nullptr, nullptr, nullptr) != napi_ok || count != 0) {
        napi_throw_type_error(env, nullptr, "No arguments are accepted");
        return nullptr;
    }
    napi_value result = nullptr;
    napi_get_boolean(env, HandleOhosPlayerBack(), &result);
    return result;
}

static napi_value HandlePlayerKey(napi_env env, napi_callback_info info) {
    size_t count = 2;
    napi_value arguments[2];
    int32_t keyCode = 0;
    int32_t action = 0;
    if (napi_get_cb_info(env, info, &count, arguments, nullptr, nullptr) != napi_ok || count != 2 ||
        napi_get_value_int32(env, arguments[0], &keyCode) != napi_ok ||
        napi_get_value_int32(env, arguments[1], &action) != napi_ok) {
        napi_throw_type_error(env, nullptr, "A key code and key action are required");
        return nullptr;
    }
    napi_value result = nullptr;
    napi_get_boolean(env, HandleOhosPlayerKey(keyCode, action), &result);
    return result;
}

EXTERN_C_START
static napi_value Init(napi_env env, napi_value exports) {
    androidx_compose_ui_arkui_init(env, exports);

    napi_property_descriptor descriptors[] = {
        {
            "MainArkUIViewController",
            nullptr,
            CreateMainArkUIViewController,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "UpdateOhosWindowInsets",
            nullptr,
            UpdateWindowInsets,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "UpdateOhosWindowMetrics",
            nullptr,
            UpdateWindowMetrics,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "UpdateOhosWindowChrome",
            nullptr,
            UpdateWindowChrome,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "UpdateOhosPlayerState",
            nullptr,
            UpdatePlayerState,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "IsOhosPlayerScreenVisible",
            nullptr,
            GetPlayerScreenVisible,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "IsOhosPlayerFullscreenRequested",
            nullptr,
            GetPlayerFullscreenRequested,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "GetOhosPlayerOrientationRevision",
            nullptr,
            GetPlayerOrientationRevision,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "HandleOhosComposeBack",
            nullptr,
            HandleComposeBack,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "HandleOhosPlayerBack",
            nullptr,
            HandlePlayerBack,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
        {
            "HandleOhosPlayerKey",
            nullptr,
            HandlePlayerKey,
            nullptr,
            nullptr,
            nullptr,
            napi_default,
            nullptr,
        },
    };
    napi_define_properties(
        env,
        exports,
        sizeof(descriptors) / sizeof(descriptors[0]),
        descriptors
    );
    return exports;
}
EXTERN_C_END

static napi_module entryModule = {
    .nm_version = 1,
    .nm_flags = 0,
    .nm_filename = nullptr,
    .nm_register_func = Init,
    .nm_modname = "entry",
    .nm_priv = nullptr,
    .reserved = {0},
};

extern "C" __attribute__((constructor)) void RegisterEntryModule() {
    napi_module_register(&entryModule);
}
