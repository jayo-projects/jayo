import org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION as KOTLIN_VERSION

println("Using Gradle version: ${gradle.gradleVersion}")
println("Using Kotlin compiler version: $KOTLIN_VERSION")
println("Using Java compiler version: ${JavaVersion.current()}")

plugins {
    id("jayo-commons")
    id("jayo.build.optional-dependencies")
}

dependencies {
    optional("org.jetbrains.kotlin:kotlin-stdlib")

    testImplementation("org.apache.tomcat.embed:tomcat-embed-core:10.1.28")
}
