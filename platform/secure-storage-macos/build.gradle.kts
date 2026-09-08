plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}


dependencies {
    add("commonMainApi", project(":core:secure-credential-api"))
    add("desktopMainImplementation", project(":platform:secure-storage-core"))
    add("desktopMainImplementation", libs.kotlinx.coroutines.core)
    add("desktopTestImplementation", libs.kotlinx.coroutines.core)
}

val keychainNativeSource = layout.projectDirectory.file("src/nativeMain/objc/MaFeiKeychain.m")
val keychainNativeLibrary = layout.buildDirectory.file("generated/keychain-native/native/macos-arm64/libmafei_keychain.dylib")
val runningJavaHome = providers.systemProperty("java.home").get()
val discoveredJniHome = if (file("$runningJavaHome/include/jni.h").isFile || !System.getProperty("os.name").startsWith("Mac")) {
    providers.provider { runningJavaHome }
} else {
    // Some IDE JBR bundles omit JNI headers. Discover an already installed full
    // JDK through macOS, without hardcoded user paths or downloading a toolchain.
    providers.exec {
        commandLine("/usr/libexec/java_home", "-v", "21")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().ifEmpty { runningJavaHome } }
}
val jniJavaHome = providers.gradleProperty("mafeiKeychainJniJdk").orElse(discoveredJniHome).get()
val compileKeychainNative by tasks.registering(Exec::class) {
    val nativeOutputFile = keychainNativeLibrary.get().asFile
    val nativeJniInclude = file("$jniJavaHome/include")
    inputs.file(keychainNativeSource)
    inputs.property("jniJavaHome", jniJavaHome)
    outputs.file(keychainNativeLibrary)
    doFirst {
        check(System.getProperty("os.name").startsWith("Mac") && System.getProperty("os.arch") in setOf("aarch64", "arm64")) {
            "MaFei Keychain JNI requires an Apple Silicon macOS build host"
        }
        check(nativeJniInclude.resolve("jni.h").isFile) {
            "JNI headers missing: use a full JDK or set -PmafeiKeychainJniJdk=/path/to/jdk"
        }
        nativeOutputFile.parentFile.mkdirs()
    }
    commandLine("xcrun", "clang", "-arch", "arm64", "-dynamiclib", "-fobjc-arc", "-fvisibility=hidden",
        "-Wall", "-Wextra", "-Werror", "-O2", "-mmacosx-version-min=11.0",
        "-I$jniJavaHome/include", "-I$jniJavaHome/include/darwin",
        keychainNativeSource.asFile.absolutePath,
        "-framework", "Foundation", "-framework", "Security",
        "-Wl,-install_name,@rpath/libmafei_keychain.dylib",
        "-o", nativeOutputFile.absolutePath)
}

tasks.named<Copy>("desktopProcessResources") {
    dependsOn(compileKeychainNative)
    from(layout.buildDirectory.dir("generated/keychain-native"))
}

tasks.withType<Test>().configureEach {
    // Explicit opt-in: only a fresh synthetic namespace, never credential inventory.
    if (providers.gradleProperty("mafeiKeychainIntegration").orNull != "true") {
        exclude("**/MacOSKeychainNativeIntegrationTest*")
    }
}
