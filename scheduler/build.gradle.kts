import kotlin.jvm.optionals.getOrNull

plugins {
    id("jayo-commons")
    `java-test-fixtures`
}

val versionCatalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(lib: String) =
    versionCatalog.findVersion(lib).getOrNull()?.requiredVersion
        ?: throw GradleException("Version '$lib' is not specified in the toml version catalog")

dependencies {
    testImplementation(project(":jayo"))
    testImplementation(testFixtures(project(":jayo")))

    testFixturesApi(project(":jayo"))
    testFixturesImplementation("org.assertj:assertj-core:${catalogVersion("assertj")}")
    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib")
}

kotlin {
    jvmToolchain(17)
}
