cmake_minimum_required(VERSION 3.16)
project(mafeisharedbridge)

set(NATIVERENDER_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})
set(MAFEI_SHARED_ROOT_PATH ${NATIVERENDER_ROOT_PATH}/../libs/${OHOS_ARCH})

add_library(mafei_shared SHARED IMPORTED)
set_target_properties(mafei_shared PROPERTIES
    IMPORTED_LOCATION ${MAFEI_SHARED_ROOT_PATH}/libmafei_shared.so
)

add_library(mafeisharedbridge SHARED
    napi_init.cpp
    generated/KmpExports.generated.cpp
)

find_library(hilog-lib hilog_ndk.z REQUIRED)
find_library(napi-lib ace_napi.z REQUIRED)

target_link_libraries(mafeisharedbridge PUBLIC
    libace_napi.z.so
    libhilog_ndk.z.so
    ${hilog-lib}
    ${napi-lib}
    mafei_shared
)
