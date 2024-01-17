import org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION as KOTLIN_VERSION

println("Using Gradle version: ${gradle.gradleVersion}")
println("Using Kotlin compiler version: $KOTLIN_VERSION")
println("Using Java compiler version: ${JavaVersion.current()}")

plugins {
    id("jayo.build.optional-dependencies")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    optional("org.jetbrains.kotlin:kotlin-stdlib")
}
