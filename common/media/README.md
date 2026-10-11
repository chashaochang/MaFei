# Media artwork

Media pages share `MediaArtwork` and an account-owned `rememberArtworkMemoryCache`.
Coil 3 owns image decoding and memory/disk LRU storage. Provider-specific requests
remain in `SessionArtwork`; `ArtworkFetcher` bridges that existing callback into
Coil instead of constructing a second HTTP client.

## Platforms

- OHOS/iOS: CPF `coil-compose:3.3.0-1.0.0`.
- Android/Mac Desktop: upstream `coil-compose:3.3.0`.
- The CPF Android variant contains JVM 18 bytecode. Root Gradle resolves only
  Android Coil dependencies to upstream; MaFei's JVM 11 target remains unchanged.
- OHOS initializes the bounded disk cache under its real private directory via
  `configureOhosArtworkCache`. Android/iOS/Mac use their application cache directory.

## Behavior

- Keep the existing ordered artwork candidates, retry budget, warm-start images,
  prefetching and reveal animation.
- Render ordinary images using Coil's native painter. Existing bitmap/blur
  consumers reuse a derived bitmap entry in the same Coil memory LRU.
- Normal artwork: 64 MiB memory, 4 MiB encoded-body limit, 960px maximum width.
  The full-resolution photo viewer retains its separate 96 MiB / 16 MiB / 4096px budget.
- One shared 128 MiB disk LRU per application, with a 12-hour entry lifetime.
  Keys incorporate the saved account namespace, complete image URL/version and
  requested width; stored keys are hashed. Metadata contains only a timestamp.
- Account changes dispose the old loader. Missing/invalid images have a short
  negative cache; cancellation and temporary transport failures remain retryable.
- Damaged/stale/unavailable disk cache falls back to the provider callback.

## Checks

```bash
./gradlew :common:media:desktopTest
./gradlew :composeApp:desktopTest --tests '*SessionArtworkTest'
./gradlew :composeApp:assembleDebug :common:media:compileKotlinIosArm64
./gradlew :composeApp:publishDebugBinariesToHarmonyApp verifyArchitecture
```

Compilation and fixture tests do not measure real-device frame rate or prove
every server's artwork is available.
