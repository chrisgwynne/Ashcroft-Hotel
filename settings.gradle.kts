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

rootProject.name = "Ripple"

include(":app")
include(":core:model")
include(":core:database")
include(":core:simulation")
include(":core:decision")
include(":core:world")
include(":core:rendering")
include(":core:designsystem")
include(":core:testing")
include(":feature:hotel")
include(":feature:person")
include(":feature:timeline")
include(":feature:history")
include(":feature:relationships")
include(":feature:settings")
include(":benchmark")
