#include <napi/native_api.h>

#include "generated/KmpExports.generated.h"

namespace {
napi_value Init(napi_env env, napi_value exports) {
    RegisterGeneratedKmpExports(env, exports);
    return exports;
}

__attribute__((constructor)) void RegisterMaFeiSharedBridgeModule() noexcept {
    napi_module module{
        .nm_version = 1,
        .nm_flags = 0U,
        .nm_filename = nullptr,
        .nm_register_func = &Init,
        .nm_modname = "libmafeisharedbridge",
        .nm_priv = nullptr,
        .reserved = {}
    };

    napi_module_register(&module);
}
}
