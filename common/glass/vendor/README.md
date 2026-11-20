# CMPLiquidGlass source baseline

Source: https://github.com/chashaochang/CMPLiquidGlass
Commit: `8c328fdd5032b05f0d3d8065e5cae3e2e95bd7e9`
Original upstream: https://github.com/Kyant0/AndroidLiquidGlass

`liquidglass/` is a trimmed Apache-2.0 source snapshot. MaFei keeps the
background capture and sampling, Compose blur, shapes, and the original
license. The runtime shader, lens, highlight and shadow implementations
are not included. The original module directories are preserved for diffs.

MaFei owns the build wiring: CPF Kotlin 2.2.21-1.3.0 / Compose 1.9.2-1.3.0,
official Compose 1.9.2 for Desktop, Fusion OHOS backend. Android uses androidMain;
iOS and Desktop use skikoMain; OHOS uses the fork's ohosMain implementation.
Do not expose vendor types through MaFei-facing APIs.

Desktop common/expect sources live in an intermediate `desktopGlassMain` source
set; Skiko actuals remain in `desktopMain` (not in the same source set).

MaFei keeps its backdrop sampling and recording optimizations. Modified files
carry MaFei modification notices; the upstream license remains in
`liquidglass/LICENSE`.
