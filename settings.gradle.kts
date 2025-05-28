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
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots") {
            content {
                includeGroup("com.eygraber")
            }
        }
    }
}

include(
    ":library",
    //":sample"
)
