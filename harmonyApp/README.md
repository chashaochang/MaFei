# HarmonyOS thin host

This directory follows the CPF-KMP-CMP 1.1.0 host shape. Generated libkn.so
and libkn_api.h are published by:

```bash
./gradlew :composeApp:publishDebugBinariesToHarmonyApp
```

The pinned toolchain is Kotlin `2.2.21-1.1.0`, Compose and the OHPM host
`1.9.2-1.1.0`, with Skiko `0.9.22.2-1.1.0` and the Skia renderer. Versions
follow the [official CPF 1.1.0 release notes](https://atomgit.com/CPF-KMP-CMP/docs/blob/KMP-v2.2.21-1.1.0/zh-cn/release-notes/KMP-v2.2.21-1.1.0%26CMP-v1.9.2-1.1.0.md).

Then run ohpm install and Hvigor assembleHap inside this directory. Only
arm64-v8a is part of the MaFei release matrix.

With DevEco's Node on PATH and `DEVECO_SDK_HOME` set to its SDK, run in this
directory (use the hvigorw.js from the same DevEco installation):

```bash
ohpm install --all
node /path/to/DevEco/Contents/tools/hvigor/bin/hvigorw.js \
  --mode module -p module=entry@default -p product=default assembleHap
```

From the repository root, validate the actual debug HAP:

```bash
node scripts/verify-harmony-artifacts.mjs
```

This checks the source/staged libkn and header pair, Hvigor copy-in, the build
ID retained through native stripping, packaged stripped library bytes,
ARM64-only native entries, and the compiled Compose resource's exact path and
bytes. It does not install the HAP or prove runtime linkage. By default the
host has no signing configuration and emits an unsigned HAP. User signing and
an attached HarmonyOS ARM device are required for installation and first frame.
Do not substitute an x64 emulator or import production signing credentials.
