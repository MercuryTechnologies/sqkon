plugins {
    alias(libs.plugins.multiplatform).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.sqlDelight).apply(false)
    alias(libs.plugins.axion.release)
}

scmVersion {
    nextVersion { suffix.set("alpha") }
    versionCreator("versionWithCommitHash")
}

version = scmVersion.version

subprojects {
    project.version = rootProject.version
}
