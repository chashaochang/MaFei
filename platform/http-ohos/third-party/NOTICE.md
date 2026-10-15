# CPF Ktor Curl HTTP engine

Derived from `io.ktor:ktor-client-curl:3.3.3-1.0.0`, Apache License 2.0.
Copyright 2014-2025 JetBrains s.r.o and contributors. Original per-file notices are retained.

Source artifact: https://maven.eazytec-cloud.com/nexus/repository/maven-releases/io/ktor/ktor-client-curl/3.3.3-1.0.0/ktor-client-curl-3.3.3-1.0.0-sources.jar
SHA-256: `a765feff92389ef8d40260c00c1ca82a4c7991c4c4fd51849355908ead19ba4a`.

MaFei modifications (2026-09-08): package relocation; HTTP-only engine (WebSocket
implementation omitted); request cancellation and handle ownership fixes; propagation of
partial-body errors; native callback/header handling fixes. See the module README for
verification boundaries. This is a local patch, not an unmodified or upstream-certified build.

The libcurl/OpenSSL binaries are supplied by the unchanged CPF cinterop artifact,
not rebuilt or copied into source control. Their upstream licensing requirements still apply.
