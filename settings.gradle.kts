pluginManagement {
    repositories {
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-releases/")
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
    }
}

rootProject.name = "mafei"

include(
    ":core:domain",
    ":core:http-contract",
    ":core:serialization",
    ":core:storage-api",
    ":core:secure-credential-api",

    ":providers:jellyfin:wire",
    ":providers:jellyfin:transport",
    ":providers:jellyfin:core",
    ":providers:jellyfin:discovery",
    ":providers:jellyfin:auth",
    ":providers:jellyfin:catalog",
    ":providers:jellyfin:search",
    ":providers:jellyfin:playstate",
    ":providers:jellyfin:playback",
    ":providers:jellyfin:images",
    ":providers:jellyfin:music",
    ":providers:jellyfin:photos",
    ":providers:jellyfin:livetv",
    ":providers:jellyfin:management",
    ":providers:jellyfin:facade",
    ":providers:jellyfin:testkit",

    ":providers:feiniu:core",
    ":providers:feiniu:connection",
    ":providers:feiniu:auth",
    ":providers:feiniu:catalog",
    ":providers:feiniu:playback",
    ":providers:feiniu:assets",
    ":providers:feiniu:account",
    ":providers:feiniu:management",
    ":providers:feiniu:facade",
    ":providers:feiniu:testkit",

    ":data:adapter-contract",
    ":data:adapter-jellyfin",
    ":data:adapter-feiniu",
    ":data:repositories",
    ":data:migration",

    ":common:core",
    ":common:glass",
    ":common:design-system",
    ":common:ui",
    ":common:media",

    ":player:api",
    ":player:core",
    ":player:ui",
    ":player:runtime-android",
    ":player:runtime-ios",
    ":player:runtime-ohos",
    ":player:runtime-macos",
    ":player:facade",
    ":player:testkit",

    ":platform:http-android",
    ":platform:http-ios",
    ":platform:http-ohos",
    ":platform:http-macos",
    ":platform:secure-storage-android",
    ":platform:secure-storage-ios",
    ":platform:secure-storage-ohos",
    ":platform:secure-storage-macos",

    ":feature:connect",
    ":feature:home",
    ":feature:media",
    ":feature:detail",
    ":feature:search",
    ":feature:favorite",
    ":feature:chasing",
    ":feature:music",
    ":feature:photo",
    ":feature:livetv",
    ":feature:player",
    ":feature:management",
    ":feature:settings",

    ":composeApp",
)
