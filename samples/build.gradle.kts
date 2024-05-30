import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Disabled

plugins {
    id("jayo-commons")
}

dependencies {
    api(project(":jayo"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

kotlin {
    explicitApi = Disabled
}
