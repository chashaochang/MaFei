package com.kyant.backdrop

actual fun isRenderEffectSupported(): Boolean = true

// Skia supports the RuntimeEffect path used by the liquid refraction effect.
actual fun isRuntimeShaderSupported(): Boolean = true

// The Skia backend uses offscreen layers for masked shadows and highlight blending.
actual fun isGraphicsLayerEffectsSupported(): Boolean = true
