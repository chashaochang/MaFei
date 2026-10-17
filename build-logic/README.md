# MaFei Gradle conventions

Each library declares exactly one target convention in its own `plugins` block.
Module names do not select targets. Keep the Kotlin, serialization, Android and
Compose plugin aliases explicit before the convention so Kotlin DSL accessors
and required plugin versions remain visible in the module.

| Convention | Targets | Additional plugins |
| --- | --- | --- |
| `mafei.kmp.shared` | Android, Desktop JVM, iOS device/simulator ARM64, OHOS ARM64 | Android library |
| `mafei.kmp.ui` | Same targets, with separate CPF/Desktop UI source trees | Android library, Compose, Compose compiler |
| `mafei.kmp.android` | Android | Android library |
| `mafei.kmp.ios` | iOS device/simulator ARM64 | None |
| `mafei.kmp.ohos` | OHOS ARM64 | None |
| `mafei.kmp.desktop` | Desktop JVM | None |

All conventions require Kotlin Multiplatform. Serialization is optional when
creating a new module. `composeApp` owns its application targets and packaging;
it is intentionally not a library convention consumer.

For a shared non-UI module:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("mafei.kmp.shared")
}
```

The root catalog (`gradle/libs.versions.toml`) is also imported by this included
build. CPF Compose and upstream Desktop Compose/Material3/Navigation are
intentionally separate pins: do not merge those versions during upgrades.
The UI convention compiles `src/sharedUiMain/kotlin` in `cpfUiMain` for
Android/iOS/OHOS and in `desktopMain` against upstream Desktop dependencies.

After changing a convention, run `./gradlew :check` and representative Kotlin
compilation/tests for the affected targets. A configuration check does not prove
Native linking, device behavior or packaging.
