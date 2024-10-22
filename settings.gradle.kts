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
        maven {
            // https://maven.pkg.github.com/MercuryTechnologies/sqkon/com/mercury/sqkon/library/1.0.0-alpha01/library-1.0.0-alpha01.pom
            val gprUser = extra["gpr.user"] as String?
            val gprKey = extra["gpr.key"] as String?
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MercuryTechnologies/sqkon")
            credentials {
                username = gprUser ?: System.getenv("GITHUB_ACTOR")
                password = gprKey ?: System.getenv("GITHUB_TOKEN")
            }
            content {
                includeGroup("com.mercury.sqkon")
            }
        }
    }
}

include(
    ":library",
    ":sample"
)
