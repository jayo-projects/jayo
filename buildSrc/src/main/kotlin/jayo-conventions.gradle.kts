import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.charset.StandardCharsets

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    `java-library`
    `maven-publish`
    id("org.jetbrains.kotlinx.kover")
}

val koverage = mapOf(
    "jayo" to 86,
    "jayo-3p-kotlinx-serialization" to 56,
)

kotlin {
    explicitApi()
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jspecify:jspecify:${property("jspecifyVersion")}")

    testImplementation(platform("org.junit:junit-bom:${property("junitVersion")}"))
    testImplementation("org.assertj:assertj-core:${property("assertjVersion")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation(kotlin("test"))

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.slf4j:slf4j-simple:${property("slf4jVersion")}")
    testRuntimeOnly("org.slf4j:slf4j-jdk-platform-logging:${property("slf4jVersion")}")
}

koverReport {
    defaults {
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

tasks {
    withType<JavaCompile> {
        options.encoding = StandardCharsets.UTF_8.toString()
        options.release = 21

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
    
    withType<KotlinCompile> {
        kotlinOptions {
            languageVersion = "1.8" // switch to "2.0" with K2 compiler when stable
            apiVersion = "1.8" // switch to "2.0" with K2 compiler when stable
            javaParameters = true
            allWarningsAsErrors = true
            freeCompilerArgs += arrayOf(
                "-Xjvm-default=all",
                "-Xnullability-annotations=@org.jspecify.annotations:strict" // not really sure if this helps ;)
            )
        }
    }

    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = true
        }
    }
}

// Generate and add javadoc and html-doc jars in jvm artefacts
val dokkaJavadocJar by tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

val dokkaHtmlJar by tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("html-doc")
}

java {
    withSourcesJar()
}

publishing.publications.withType<MavenPublication> {
    from(components["java"])

    artifact(dokkaJavadocJar)
    artifact(dokkaHtmlJar)
}
