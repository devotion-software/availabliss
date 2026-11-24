// Top-level build file. Plugins are declared here and applied per module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.sentry) apply false
    alias(libs.plugins.spotless)
}

// Formatting and lint: ktlint (rules in .editorconfig) over all Kotlin sources and build scripts.
// `./gradlew spotlessCheck` fails on a violation; `./gradlew spotlessApply` fixes what ktlint can.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("app/src/**/*.kt")
        ktlint(ktlintVersion).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(ktlintVersion).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
}
