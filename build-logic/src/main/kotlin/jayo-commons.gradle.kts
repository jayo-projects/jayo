import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Strict
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
import kotlin.jvm.optionals.getOrNull

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka-javadoc")
    `java-library`
    `maven-publish`
    id("org.jetbrains.kotlinx.kover")
}

val versionCatalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun catalogVersion(lib: String) =
    versionCatalog.findVersion(lib).getOrNull()?.requiredVersion
        ?: throw GradleException("Version '$lib' is not specified in the toml version catalog")

val isCI = providers.gradleProperty("isCI")

val koverage = mapOf(
    "jayo" to 86,
    "jayo-3p-kotlinx-serialization" to 54,
    "jayo-scheduler" to 84,
)

kotlin {
    // for all targets: main / test / testFixtures
    compilerOptions {
        languageVersion.set(KOTLIN_2_0)
        apiVersion.set(KOTLIN_2_0)
        explicitApi = Strict
        freeCompilerArgs.addAll(
            "-Xnullability-annotations=@org.jspecify.annotations:strict", // not really sure if this helps ;)
            "-opt-in=kotlin.contracts.ExperimentalContracts",
        )
    }
    // only for the main target
    target {
        val main by compilations.getting {
            compileTaskProvider.configure {
                compilerOptions {
                    allWarningsAsErrors = true
                }
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.jspecify:jspecify:${catalogVersion("jspecify")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${catalogVersion("junit")}")
    testImplementation("org.assertj:assertj-core:${catalogVersion("assertj")}")
    testImplementation(kotlin("test"))

    testRuntimeOnly("org.slf4j:slf4j-jdk-platform-logging:${catalogVersion("slf4j")}")
    testRuntimeOnly("ch.qos.logback:logback-classic:${catalogVersion("logback")}")
}

kover {
    reports {
        verify {
            rule {
                bound {
                    minValue = koverage[project.name]
                }
            }
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.toString()

        // replace '-' with '.' to match JPMS jigsaw module name
        val jpmsName = project.name.replace('-', '.')
        // this is needed because we have a separate compile step because the Java code is in 'main/java' and the Kotlin
        // code is in 'main/kotlin'
        options.compilerArgs.addAll(
            listOf(
                "--patch-module",
                "$jpmsName=${sourceSets.main.get().output.asPath}",
                //"--enable-preview",
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
        //jvmArgs("--enable-preview")
    }
}

// Generate a javadoc jar for Java and Kotlin code in jvm artifacts.
val dokkaJavadocJar by tasks.registering(Jar::class) {
    description = "A Javadoc JAR containing Dokka Javadoc for Java and Kotlin"
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

publishing.publications.withType<MavenPublication> {
    from(components["java"])

    artifact(dokkaJavadocJar)
}
