import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.DontIncludeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.IncludeResourceTransformer
import kotlin.jvm.optionals.getOrNull
import org.gradle.api.file.DuplicatesStrategy.WARN

val versionCatalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(lib: String) =
    versionCatalog.findVersion(lib).getOrNull()?.requiredVersion
        ?: throw GradleException("Version '$lib' is not specified in the toml version catalog")

println("Benchmarks are using JMH version: ${catalogVersion("jmh")}")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jmh)
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

dependencies {
    jmh(project(":third-party:jayo-3p-kotlinx-serialization"))

    jmh("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:${catalogVersion("kotlinxSerialization")}")
    jmh("com.squareup.okio:okio:${catalogVersion("okio")}")
    jmh("com.fasterxml.jackson.module:jackson-module-kotlin:${catalogVersion("jackson")}")

    jmhRuntimeOnly("org.slf4j:slf4j-jdk-platform-logging:${catalogVersion("slf4j")}")
    jmhRuntimeOnly("ch.qos.logback:logback-classic:${catalogVersion("logback")}")
}

kotlin {
    jvmToolchain(21)
}

jmh {
    jvmArgs = listOf("-Djmh.separateClasspathJAR=true", "-Dorg.gradle.daemon=false", "-Djmh.executor=VIRTUAL")
    duplicateClassesStrategy = WARN
    jmhVersion = catalogVersion("jmh")

//    includes.set(listOf("""jayo\.benchmarks\.BufferLatin1Benchmark.*"""))
    includes.set(listOf("""jayo\.benchmarks\.BufferUtf8Benchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.JsonSerializationBenchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.SlowReaderBenchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.SlowWriterBenchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.SocketReaderBenchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.TcpAndJsonSerializationBenchmark.*"""))
}

tasks {
    val shadowJmh by registering(ShadowJar::class) {
        dependsOn("jmhJar")

        transform(DontIncludeResourceTransformer().apply {
            resource = "META-INF/BenchmarkList"
        })

        transform(IncludeResourceTransformer().apply {
            resource = "META-INF/BenchmarkList"
            file = file("${project.layout.buildDirectory.get()}/jmh-generated-resources/META-INF/BenchmarkList")
        })
    }

    val assemble by getting {
        dependsOn(shadowJmh)
    }
}
