import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Disabled
import kotlin.jvm.optionals.getOrNull

plugins {
    id("jayo-commons")
}

val versionCatalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(lib: String) =
    versionCatalog.findVersion(lib).getOrNull()?.requiredVersion
        ?: throw GradleException("Version '$lib' is not specified in the toml version catalog")

dependencies {
    api(project(":jayo"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    runtimeOnly("org.slf4j:slf4j-simple:${catalogVersion("slf4j")}")
    runtimeOnly("org.slf4j:slf4j-jdk-platform-logging:${catalogVersion("slf4j")}")
}

kotlin {
    explicitApi = Disabled
}
