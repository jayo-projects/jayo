import kotlin.jvm.optionals.getOrNull

plugins {
    id("jayo-commons")
    alias(libs.plugins.kotlin.serialization)
}

val versionCatalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(lib: String) =
    versionCatalog.findVersion(lib).getOrNull()?.requiredVersion
        ?: throw GradleException("Version '$lib' is not specified in the toml version catalog")

dependencies {
    api(project(":jayo"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${catalogVersion("kotlinxSerialization")}")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = false
    }

    sourceSets {
        configureEach {
            languageSettings {
                optIn("kotlinx.serialization.json.internal.JsonFriendModuleApi")
            }
        }
    }
}
