# CMPLiquidGlass source baseline

Source: https://github.com/chashaochang/CMPLiquidGlass
Commit: `8c328fdd5032b05f0d3d8065e5cae3e2e95bd7e9`
Original upstream: https://github.com/Kyant0/AndroidLiquidGlass

`liquidglass/` contains only `backdrop/src`, `shapes/src`, and the upstream
Apache-2.0 LICENSE, exported from that committed tree (no working-copy changes).
The original module directories are preserved for future diffs. MaFei compiles
both within `common:glass`, its sole rendering adapter. Demo code, generated
binaries, and upstream build scripts are not imported.

MaFei owns the build wiring: CPF Kotlin 2.2.21-1.0.0 / Compose 1.9.2-1.0.0,
official Compose 1.9.2 for Desktop, Skia OHOS backend. Android uses androidMain;
iOS and Desktop use skikoMain; OHOS uses the fork's ohosMain implementation.
Do not expose vendor types through MaFei-facing APIs.

Local compatibility patch: `skikoMain/.../internal/RenderEffect.kt` uses the
public `asSkiaImageFilter()` API instead of the removed `skiaImageFilter`
property, matching Compose 1.9.2 and the fork's existing OHOS implementation.
`skikoMain` also uses `Paint.asFrameworkPaint()` instead of `skiaPaint`, and
returns the Skia shader directly (Compose's `Shader` is a typealias here).
Desktop common/expect sources live in an intermediate `desktopGlassMain` source
set; Skiko actuals remain in `desktopMain` (not in the same source set).
