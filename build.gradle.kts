import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.multiplatform).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.sqlDelight).apply(false)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.maven.publish).apply(false)
}

group = findProperty("GROUP").toString()
version = System.getenv("RELEASE_VERSION")?.takeIf { it.isNotBlank() }
    ?: findProperty("VERSION_NAME").toString()

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            logger.lifecycle("Configuring Maven Publishing for ${name}:${version}")
            publishToMavenCentral(automaticRelease = true)
            signAllPublications()
        }
        extensions.configure<PublishingExtension> {
            logger.lifecycle("Publishing ${project.name}:${version}")
            publications.withType<MavenPublication>().configureEach {
                this.version = project.version.toString()
            }
            repositories {
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
    inputs.property("version", project.version)
    doLast {
        inputs.properties["version"]?.let { println("Version: $it") }
    }
}