// Modified by MaFei: keep only the blur capability check.
package com.kyant.backdrop

// With CPF 1.3.0 Fusion, the backdrop capture path can leave the page blank.
// Keep the opaque fallback until capture and replay are verified on device.
actual fun isRenderEffectSupported(): Boolean = false
