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

tasks {
    withType<Test> {
        useJUnitPlatform {
            // override security properties enabling all options
            systemProperty("java.security.properties", "java.security.override")
        }
    }
}
