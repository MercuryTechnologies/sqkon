@file:Suppress("UnstableApiUsage")

rootProject.name = "sqkon"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            setUrl("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github.*")
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        mavenLocal {
            content {
                includeGroup("com.mercury.sqkon")
            }
        }
    }
}

include(
    ":library",
    //":sample"
)
