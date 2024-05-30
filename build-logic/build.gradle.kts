plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("optionalDependenciesPlugin") {
            id = "jayo.build.optional-dependencies"
            implementationClass = "jayo.build.OptionalDependenciesPlugin"
        }
    }
}
