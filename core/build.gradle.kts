import java.util.*
import kotlin.jvm.optionals.getOrNull
import org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION as KOTLIN_VERSION

println("Using Gradle version: ${gradle.gradleVersion}")
println("Using Kotlin compiler version: $KOTLIN_VERSION")
println("Using Java compiler version: ${JavaVersion.current()}")

plugins {
    id("jayo-commons")
    id("jayo.build.optional-dependencies")
    `java-test-fixtures`
    alias(libs.plugins.mrjar)
}

multiRelease {
    targetVersions(17, 21, 25)
}

// Temporary workaround for https://github.com/melix/mrjar-gradle-plugin/issues/3.
// Remove this block when https://github.com/melix/mrjar-gradle-plugin/pull/10 is released.
configurations.matching { config -> config.name.startsWith("java21") }
    .configureEach {
        val noPrefix = this.name
            .replace("java21", "")
            .replace("java25", "")
        val sharedName = "${noPrefix.take(1).lowercase(Locale.getDefault())}${noPrefix.substring(1)}"
        val sharedConfig = configurations.findByName(sharedName)

        if (sharedConfig != null) {
            this.extendsFrom(sharedConfig)
        }
    }

val versionCatalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(lib: String) =
    versionCatalog.findVersion(lib).getOrNull()?.requiredVersion
        ?: throw GradleException("Version '$lib' is not specified in the toml version catalog")

dependencies {
    optional("org.jetbrains.kotlin:kotlin-stdlib")

    implementation("org.jspecify:jspecify:${catalogVersion("jspecify")}")

    // These compileOnly dependencies must also be listed in the OSGi configuration above (todo).
    compileOnly("org.bouncycastle:bctls-jdk18on:${catalogVersion("bouncycastle")}")
    compileOnly("org.conscrypt:conscrypt-openjdk-uber:${catalogVersion("conscrypt")}")

    testFixturesApi("org.junit.jupiter:junit-jupiter:${catalogVersion("junit")}")
    testFixturesApi("org.bouncycastle:bcprov-jdk18on:${catalogVersion("bouncycastle")}")
    testFixturesApi("org.conscrypt:conscrypt-openjdk-uber:${catalogVersion("conscrypt")}")

    // classifier for AmazonCorrettoCryptoProvider = linux-x86_64
    testFixturesApi("software.amazon.cryptools:AmazonCorrettoCryptoProvider:${catalogVersion("amazonCorretto")}:linux-x86_64")
    testFixturesImplementation("org.bouncycastle:bcpkix-jdk18on:${catalogVersion("bouncycastle")}")
    testFixturesImplementation("org.bouncycastle:bctls-jdk18on:${catalogVersion("bouncycastle")}")
    testFixturesImplementation("org.assertj:assertj-core:${catalogVersion("assertj")}")
    testFixturesImplementation("ch.qos.logback:logback-classic:${catalogVersion("logback")}")
}

tasks {
    withType<Test> {
        useJUnitPlatform {
            // override security properties enabling all options
            systemProperty("java.security.properties", "java.security.override")
        }
    }
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
