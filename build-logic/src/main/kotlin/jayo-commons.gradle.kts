import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Strict
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
import java.nio.charset.StandardCharsets
import kotlin.jvm.optionals.getOrNull

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    `java-library`
    `maven-publish`
    id("org.jetbrains.kotlinx.kover")
}

val versionCatalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun catalogVersion(lib: String) =
    versionCatalog.findVersion(lib).getOrNull()?.requiredVersion
        ?: throw GradleException("Version '$lib' is not specified in the toml version catalog")
val javaVersion = catalogVersion("java").toInt()

val isCI = providers.gradleProperty("isCI")

val koverage = mapOf(
    "jayo" to if (isCI.isPresent) 83 else 85,
    "jayo-3p-kotlinx-serialization" to 55,
)

kotlin {
    compilerOptions {
        languageVersion.set(KOTLIN_2_0)
        apiVersion.set(KOTLIN_2_0)
        javaParameters = true
        allWarningsAsErrors = true
        explicitApi = Strict
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-Xnullability-annotations=@org.jspecify.annotations:strict", // not really sure if this helps ;)
            "-opt-in=kotlin.contracts.ExperimentalContracts",
        )
    }

    jvmToolchain(javaVersion)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jspecify:jspecify:${catalogVersion("jspecify")}")

    testImplementation(platform("org.junit:junit-bom:${catalogVersion("junit")}"))
    testImplementation("org.assertj:assertj-core:${catalogVersion("assertj")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation(kotlin("test"))

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.slf4j:slf4j-simple:${catalogVersion("slf4j")}")
    testRuntimeOnly("org.slf4j:slf4j-jdk-platform-logging:${catalogVersion("slf4j")}")
}

kover {
    reports {
        total {
            verify {
                onCheck = true
                rule {
                    bound {
                        minValue = koverage[project.name]
                    }
                }
            }
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = StandardCharsets.UTF_8.toString()
        options.release = javaVersion

        // replace '-' with '.' to match JPMS jigsaw module name
        val jpmsName = project.name.replace('-', '.')
        // this is needed because we have a separate compile step because the Java code is in 'main/java' and the Kotlin
        // code is in 'main/kotlin'
        options.compilerArgs.addAll(
            listOf(
                "--patch-module",
                "$jpmsName=${sourceSets.main.get().output.asPath}",
            )
        )
    }

    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    withType<Test> {
        useJUnitPlatform {
            if (isCI.isPresent) {
                excludeTags("no-ci")
            } else {
                excludeTags("slow")
            }
        }
        testLogging {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = true
        }
    }

    withType<Javadoc> {
        (options as StandardJavadocDocletOptions)
            .addStringOption("Xdoclint:none", "-quiet")
    }
}

// Generate html-doc jar for Kotlin code in jvm artefacts.
// "javadoc" Dokka classifier clashes with the `withJavadocJar()` option below
val dokkaHtmlJar by tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("html-doc")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing.publications.withType<MavenPublication> {
    from(components["java"])

    artifact(dokkaHtmlJar)
}
