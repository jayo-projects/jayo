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
    targetVersions(17, 21)
}

val versionCatalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(lib: String) =
    versionCatalog.findVersion(lib).getOrNull()?.requiredVersion
        ?: throw GradleException("Version '$lib' is not specified in the toml version catalog")

dependencies {
    optional("org.jetbrains.kotlin:kotlin-stdlib")

    api("org.jspecify:jspecify:${catalogVersion("jspecify")}")

    // These compileOnly dependencies must also be listed in the OSGi configuration above (todo).
    compileOnly("org.bouncycastle:bctls-jdk18on:${catalogVersion("bouncycastle")}")
    compileOnly("org.conscrypt:conscrypt-openjdk-uber:${catalogVersion("conscrypt")}")

    testFixturesImplementation(platform("org.junit:junit-bom:${catalogVersion("junit")}"))

    testFixturesApi("org.junit.jupiter:junit-jupiter-api")
    testFixturesApi("org.junit.jupiter:junit-jupiter-params")
    testFixturesApi("org.hamcrest:hamcrest:${catalogVersion("hamcrest")}")
    testFixturesApi("org.bouncycastle:bcprov-jdk18on:${catalogVersion("bouncycastle")}")
    testFixturesApi("org.conscrypt:conscrypt-openjdk-uber:${catalogVersion("conscrypt")}")
    // classifier for AmazonCorrettoCryptoProvider = linux-x86_64
    testFixturesApi("software.amazon.cryptools:AmazonCorrettoCryptoProvider:${catalogVersion("amazonCorretto")}:linux-x86_64")
    testFixturesImplementation("org.bouncycastle:bcpkix-jdk18on:${catalogVersion("bouncycastle")}")
    testFixturesImplementation("org.bouncycastle:bctls-jdk18on:${catalogVersion("bouncycastle")}")
}

tasks {
    withType<Test> {
        useJUnitPlatform {
            // override security properties enabling all options
            systemProperty("java.security.properties", "java.security.override")
        }
    }
}
