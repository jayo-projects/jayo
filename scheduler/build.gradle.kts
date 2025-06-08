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

// Generate a source jar for main and testFixtures in jvm artifacts.
val sourcesJar by tasks.registering(Jar::class) {
    description = "A Source JAR containing sources for main and testFixtures"
    from(sourceSets.main.get().allSource, sourceSets.testFixtures.get().allSource)
    archiveClassifier.set("sources")
}

publishing.publications.withType<MavenPublication> {
    artifact(sourcesJar)
}

