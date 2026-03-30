# CMakeLists.txt for MPV Native Module
cmake_minimum_required(VERSION 3.16)
project(mpvnative)

set(NATIVERENDER_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})

set(MPV_ROOT_PATH ${NATIVERENDER_ROOT_PATH}/../libs/${OHOS_ARCH})

include_directories(${NATIVERENDER_ROOT_PATH}
                    ${MPV_ROOT_PATH}/include)

add_library(mpvnative SHARED
    napi_init.cpp
    mpv/mpv_wrapper.cpp
    mpv/mpv_subtitle.cpp
    mpv/mpv_audio.cpp
    mpv/mpv_video_info.cpp
    mpv/mpv_config.cpp
    mpv/mpv_playback.cpp
    mpv/mpv_surface.cpp
    mpv/mpv_loader.cpp
    mpv/mpv_network.cpp
)

find_library(ace-lib ace_ndk.z REQUIRED)
find_library(hilog-lib hilog_ndk.z REQUIRED)
find_library(napi-lib ace_napi.z REQUIRED)
find_library(rawfile-lib rawfile.z REQUIRED)
find_library(uv-lib uv REQUIRED)
find_library(native_window-lib native_window REQUIRED)
find_library(EGL-lib EGL REQUIRED)
find_library(GLESv3-lib GLESv3 REQUIRED)

target_link_libraries(mpvnative PUBLIC
    libace_napi.z.so
    libhilog_ndk.z.so
    ${ace-lib}
    ${hilog-lib}
    ${rawfile-lib}
    ${napi-lib}
    ${uv-lib}
    ${native_window-lib}
    ${EGL-lib}
    ${GLESv3-lib}
    ${MPV_ROOT_PATH}/libmpv.so.2
)