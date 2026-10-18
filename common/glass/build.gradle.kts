plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    id("mafei.kmp.ui")
}


dependencies {
    add("commonMainApi", project(":common:core"))
    add("desktopTestImplementation", libs.compose.desktop)
}

// The two upstream source trees are pinned together; their Demo/build scripts
// are excluded. Reuse MaFei's CPF versus official Desktop dependency split.
kotlin {
    sourceSets {
        val desktopGlassMain by creating {
            dependsOn(getByName("commonMain"))
            dependencies {
                implementation(libs.compose.upstream.runtime)
                implementation(libs.compose.upstream.foundation)
                implementation(libs.compose.upstream.ui)
            }
        }
        getByName("desktopMain").dependsOn(desktopGlassMain)
        listOf("cpfUiMain", "desktopGlassMain").forEach { name ->
            getByName(name).kotlin.apply {
                srcDir("vendor/liquidglass/backdrop/src/commonMain/kotlin")
                srcDir("vendor/liquidglass/shapes/src/commonMain/kotlin")
            }
        }
        getByName("androidMain").kotlin.srcDir("vendor/liquidglass/backdrop/src/androidMain/kotlin")
        getByName("iosMain").kotlin.srcDir("vendor/liquidglass/backdrop/src/skikoMain/kotlin")
        getByName("desktopMain").kotlin.srcDir("vendor/liquidglass/backdrop/src/skikoMain/kotlin")
        getByName("ohosMain").kotlin.srcDir("vendor/liquidglass/backdrop/src/ohosMain/kotlin")
        all { languageSettings.enableLanguageFeature("ContextParameters") }
    }
}
