cmake_minimum_required(VERSION 3.5.0)
project(mafei_cmp)

set(NATIVE_ROOT ${CMAKE_CURRENT_SOURCE_DIR})
set(ENTRY_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../..)

if(DEFINED PACKAGE_FIND_FILE)
    include(${PACKAGE_FIND_FILE})
endif()

include_directories(
    ${NATIVE_ROOT}
    ${NATIVE_ROOT}/include
    ${NATIVE_ROOT}/include/arm64-v8a
)

set(COMPOSE_MODULE_PATH ${ENTRY_ROOT}/oh_modules/@cpf-kmp-cmp/compose)
list(APPEND CMAKE_PREFIX_PATH ${COMPOSE_MODULE_PATH})
find_package(compose REQUIRED CONFIG)

add_library(entry SHARED napi_init.cpp)

target_link_libraries(
    entry
    PUBLIC
    libace_napi.z.so
    libhilog_ndk.z.so
    libffrt.z.so
    libohpreferences.so
    libdeviceinfo_ndk.z.so
    librawfile.z.so
    ${ENTRY_ROOT}/libs/arm64-v8a/libkn.so
    compose::skikobridge
    libc++_shared.so
)
