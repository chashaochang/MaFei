#include "libkn_api.h"
#include "napi/native_api.h"
#include <vector>

static napi_value CreateMainArkUIViewController(napi_env env, napi_callback_info info) {
    size_t count = 1;
    napi_value arguments[1];
    size_t length = 0;
    if (napi_get_cb_info(env, info, &count, arguments, nullptr, nullptr) != napi_ok || count != 1 ||
        napi_get_value_string_utf8(env, arguments[0], nullptr, 0, &length) != napi_ok || length == 0 || length > 4096) {
        napi_throw_type_error(env, nullptr, "A private files directory is required");
        return nullptr;
    }
    std::vector<char> directory(length + 1);
    size_t copied = 0;
    if (napi_get_value_string_utf8(env, arguments[0], directory.data(), directory.size(), &copied) != napi_ok || copied != length || directory[0] != '/') {
        napi_throw_type_error(env, nullptr, "Invalid private files directory");
        return nullptr;
    }
    for (size_t index = 0; index < copied; ++index) {
        if (directory[index] == '\0') {
            napi_throw_type_error(env, nullptr, "Invalid private files directory");
            return nullptr;
        }
    }
    // Kotlin copies the string during this call; no temporary pointer or
    // N-API handle escapes to a background persistence worker.
    return reinterpret_cast<napi_value>(MainArkUIViewController(env, directory.data()));
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
