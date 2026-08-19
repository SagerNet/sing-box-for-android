pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // For libghostty-android snapshots; remove after release.
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://api.xposed.info/") }
    }
}
rootProject.name = "sing-box"
include(":app")
include(":libxposed-api")
project(":libxposed-api").projectDir = file("third_party/libxposed-api")
