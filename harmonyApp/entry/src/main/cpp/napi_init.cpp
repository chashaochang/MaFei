#include "libkn_api.h"
#include "napi/native_api.h"

static napi_value CreateMainArkUIViewController(napi_env env, napi_callback_info info) {
    return reinterpret_cast<napi_value>(MainArkUIViewController(env));
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
