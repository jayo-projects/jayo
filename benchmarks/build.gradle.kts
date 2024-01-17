import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.DontIncludeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.IncludeResourceTransformer

println("Benchmarks are using JMH version: ${property("jmhVersion")}")

plugins {
    id("me.champeau.jmh")
    id("com.github.johnrengelman.shadow")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.kover") apply false
}

jmh {
    jvmArgs.set(listOf("-Djmh.separateClasspathJAR=true", "-Dorg.gradle.daemon=false", "-Djmh.executor=VIRTUAL"))
    duplicateClassesStrategy.set(DuplicatesStrategy.WARN)
    jmhVersion.set(property("jmhVersion").toString())

//    includes.set(listOf("""jayo\.benchmarks\.BufferLatin1Benchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.BufferUtf8Benchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.JsonSerializationBenchmark.*"""))
    includes.set(listOf("""jayo\.benchmarks\.SlowSinkBenchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.SlowSourceBenchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.SocketSourceBenchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.TcpAndJsonSerializationBenchmark.*"""))
//    includes.set(listOf("""jayo\.benchmarks\.TcpBenchmark.*"""))
}

dependencies {
    jmh(project(":third-party:jayo-3p-kotlinx-serialization"))

    jmh("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:${property("kotlinxSerializationVersion")}")
    jmh("com.squareup.okio:okio:${property("okioVersion")}")
    jmh("com.fasterxml.jackson.module:jackson-module-kotlin:${property("jacksonVersion")}")
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
