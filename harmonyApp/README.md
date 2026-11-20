# HarmonyOS thin host

This directory follows the CPF-KMP-CMP 1.3.0 host shape. Generated libkn.so
and libkn_api.h are published by:

```bash
./gradlew :composeApp:publishDebugBinariesToHarmonyApp
```

The pinned toolchain is Kotlin `2.2.21-1.3.0`, Compose and the OHPM host
`1.9.2-1.3.0`, with Skiko `0.9.22.2-1.3.0`. OHOS uses `fusion-renderer`;
both hosts use CPF's `Compose` component, which selects the renderer reported
by the Kotlin controller. Android, iOS and Desktop keep their existing backends.
Versions follow the [official CPF 1.3.0 release notes](https://atomgit.com/CPF-KMP-CMP/docs/blob/docs/1.3.0-release-notes/zh-cn/release-notes/KMP-v2.2.21-1.3.0%26CMP-v1.9.2-1.3.0.md).

## Debug incremental builds

Native incremental compilation is requested with `kotlin.incremental.native=true`
and `kotlin.native.cacheKind.ohos_arm64=static`. The CPF plugin can disable the
native cache for a Compose UI artifact that supports both rendering backends.
Do not bypass that guard. When switching renderers, run a clean link before
publishing the new Kotlin binaries.

Historical comparison: measured on 2026-10-11 with CPF 1.1.0 and Skia,
using the same OHOS Debug link task:

| Scenario | Native link task | Entire Gradle build |
| --- | ---: | ---: |
| Forced link, Native cache disabled | 124.75s | 130.63s |
| Forced link, warmed Native cache | 11.54s | 12.32s |
| One OHOS Kotlin file changed | 14.16s | 25.53s |

The source-change measurement used a temporary private probe without changing
application behavior; the probe was removed afterwards. Forced links used the
task-specific `--rerun`, so the comparison does not measure skipped tasks.

## Local prerequisites

The HarmonyOS player requires the separately licensed, closed-source MFPlayer
SDK. Place your authorized `mfplayer.har` at `harmonyApp/entry/libs/mfplayer.har`
before running `ohpm install --all`. The SDK and its backup archives are local
inputs, excluded from the current source tree and from the repository's GPL license.
The integration package name is `mfplayer`; do not silently substitute another
engine when the local SDK is missing.

Use the Node and OHPM bundled with the same DevEco Studio installation as Hvigor.
The latest local validation used DevEco's Node 24.14.1; a shell Node 20 installation
failed during dependency installation with `ERR_REQUIRE_ESM`.

The committed `build-profile.json5` has no signing material. Configure signing
locally in DevEco Studio when installing on a device; keep those changes out of
commits. An ignored `build-profile.private.json5` may hold a local backup, but
Hvigor does not automatically load that file. Do not commit certificates,
passwords, provisioning profiles or absolute signing paths.

## Packaging

Then run ohpm install and Hvigor assembleHap inside this directory. Only
arm64-v8a is part of the MaFei release matrix.

With DevEco's Node on PATH and `DEVECO_SDK_HOME` set to its SDK, run in this
directory (use the hvigorw.js from the same DevEco installation):

```bash
ohpm install --all
node /path/to/DevEco/Contents/tools/hvigor/bin/hvigorw.js \
  --mode module -p module=entry@default -p product=default assembleHap
```

From the repository root, validate a run-script Debug HAP:

```bash
node scripts/verify-harmony-artifacts.mjs "$PWD" \
  harmonyApp/entry/build/default/outputs/default/entry-default-signed.hap debug
```

This checks the source/staged libkn and header pair, Hvigor copy-in, the build
ID retained through native stripping, packaged stripped library bytes,
ARM64-only native entries, and the compiled Compose resource's exact path and
bytes. It does not install the HAP or prove runtime linkage. By default the
host has no signing configuration and emits an unsigned HAP. User signing and
an attached HarmonyOS ARM device are required for installation and first frame.
Do not substitute an x64 emulator or import production signing credentials.

## PC player window

On HarmonyOS PC (`deviceType === '2in1'`), `PlayerWindowLauncher` opens the
non-exported singleton `PlayerAbility` for video playback. Its Compose entry
point renders the existing session-owned player; the browser window stays
available. Selecting another video reuses the player window. Closing it stops
that playback, and window initialization failure restores the inline player.
Phones and tablets keep the inline presentation.

New player windows use the main window's current pixel dimensions. Reusing an
open player preserves its size. The native surface restores the active load
generation when it mounts, even if a device-state query has already replaced
the original load action.

The dedicated player keeps a persistent navigation/title bar above the video
in windowed mode, even when playback controls hide. System fullscreen hides
this bar and fills the window with the video. There is no separate in-window
fullscreen mode.
The PC player hides the picture-in-picture action in both windowed and fullscreen
modes. Phones and tablets keep their existing picture-in-picture controls.

The player publishes its own safe areas, native title-button bounds and
fullscreen state. `ApplicationVisibility` aggregates the two Ability lifecycles
so moving focus between the windows does not background the shared playback
runtime. The native surface resolves its owning window for brightness and
screen-awake requests.
