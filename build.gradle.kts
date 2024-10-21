import com.vanniktech.maven.publish.MavenPublishPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.multiplatform).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.sqlDelight).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
}

allprojects {
    group = findProperty("GROUP").toString()
    version = System.getenv("RELEASE_VERSION")?.takeIf { it.isNotBlank() }
        ?: findProperty("VERSION_NAME").toString()
}

subprojects {
    plugins.withType<MavenPublishPlugin>().configureEach {
        extensions.findByType<PublishingExtension>()?.also { publishing ->
            logger.lifecycle("Publishing ${project.group}:${project.name}:${project.version}")
            publishing.repositories {
                // GitHub Packages
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/MercuryTechnologies/sqkon")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

tasks.register("version") {
    notCompatibleWithConfigurationCache("Version task is not compatible with configuration cache")
    doLast {
        println("Version: $version")
    }
}