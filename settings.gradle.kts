pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OfflineP2PVoice"

include(":app")
include(":core:common")
include(":core:protocol")
include(":core:network")
include(":core:audio")
include(":feature:connection")
include(":feature:control")
include(":feature:voice")
include(":feature:music")
include(":feature:effects")
include(":feature:settings")
