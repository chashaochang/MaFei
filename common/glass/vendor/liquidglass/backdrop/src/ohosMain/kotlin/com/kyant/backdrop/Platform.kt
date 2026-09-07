package com.kyant.backdrop

actual fun isRenderEffectSupported(): Boolean = true

// Skia supports the RuntimeEffect path used by the liquid refraction effect.
actual fun isRuntimeShaderSupported(): Boolean = true

// Avoid OHOS offscreen GraphicsLayer rasterization until the renderer bridge is stable.
actual fun isGraphicsLayerEffectsSupported(): Boolean = false
