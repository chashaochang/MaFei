plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}


dependencies {
    add("commonMainApi", project(":player:api"))
    add("commonMainImplementation", project(":player:core"))
}

// JNI dylib for the AVPlayer bridge, following the proven
// platform/secure-storage-macos pattern: clang-built Apple Silicon library
// bundled into the desktop jar resources, extracted and System.load'ed by
// NativeAvPlayer at first use.
val avPlayerNativeSource = layout.projectDirectory.file("src/nativeMain/objc/MaFeiAVPlayer.m")
val avPlayerNativeLibrary = layout.buildDirectory.file("generated/avplayer-native/native/macos-arm64/libmafei_avplayer.dylib")
val runningJavaHome = providers.systemProperty("java.home").get()
val discoveredJniHome = if (file("$runningJavaHome/include/jni.h").isFile || !System.getProperty("os.name").startsWith("Mac")) {
    providers.provider { runningJavaHome }
} else {
    providers.exec {
        commandLine("/usr/libexec/java_home", "-v", "21")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().ifEmpty { runningJavaHome } }
}
val avPlayerJniHome = providers.gradleProperty("mafeiAvPlayerJniJdk").orElse(discoveredJniHome).get()

val compileAvPlayerNative by tasks.registering(Exec::class) {
    val nativeOutputFile = avPlayerNativeLibrary.get().asFile
    val nativeJniInclude = file("$avPlayerJniHome/include")
    inputs.file(avPlayerNativeSource)
    inputs.property("jniJavaHome", avPlayerJniHome)
    outputs.file(nativeOutputFile)
    doFirst {
        check(System.getProperty("os.name").startsWith("Mac") && System.getProperty("os.arch") in setOf("aarch64", "arm64")) {
            "MaFei AVPlayer JNI requires an Apple Silicon macOS build host"
        }
        check(nativeJniInclude.resolve("jni.h").isFile) {
            "JNI headers missing: use a full JDK or set -PmafeiAvPlayerJniJdk=/path/to/jdk"
        }
        nativeOutputFile.parentFile.mkdirs()
    }
    commandLine("xcrun", "clang", "-arch", "arm64", "-dynamiclib", "-fobjc-arc", "-fvisibility=hidden",
        "-Wall", "-Wextra", "-Werror", "-O2", "-mmacosx-version-min=11.0",
        "-I$avPlayerJniHome/include", "-I$avPlayerJniHome/include/darwin",
        avPlayerNativeSource.asFile.absolutePath,
        "-framework", "Foundation", "-framework", "AVFoundation", "-framework", "CoreMedia",
        "-Wl,-install_name,@rpath/libmafei_avplayer.dylib",
        "-o", nativeOutputFile.absolutePath)
}

tasks.named<Copy>("desktopProcessResources") {
    dependsOn(compileAvPlayerNative)
    from(layout.buildDirectory.dir("generated/avplayer-native"))
}
