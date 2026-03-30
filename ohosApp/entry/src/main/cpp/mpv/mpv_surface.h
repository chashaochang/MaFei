/**
 * mpv_surface.h - XComponent 回调和 Surface 管理（Surface 硬解模式）
 * 
 * Surface 模式: vo=ohcodec-osd + hwdec=auto，硬件解码直接渲染到 Surface
 */

#ifndef MPV_SURFACE_H
#define MPV_SURFACE_H

#include <js_native_api.h>
#include <mpv/client.h>
#include "ace/xcomponent/native_interface_xcomponent.h"

// XComponent 生命周期回调
void OnSurfaceCreated(OH_NativeXComponent *component, void *window);
void OnSurfaceChanged(OH_NativeXComponent *component, void *window);
void OnSurfaceDestroyed(OH_NativeXComponent *component, void *window);

// 后台初始化线程参数和入口
struct InitThreadArgs {
    mpv_handle *mpv;
    uint64_t surfaceId;
    uint64_t width;
    uint64_t height;
};
void* mpv_init_thread(void* arg);

// 导出 Surface 信息
extern uint64_t surfaceWidth;
extern uint64_t surfaceHeight;
extern void *native_window;

#endif // MPV_SURFACE_H
