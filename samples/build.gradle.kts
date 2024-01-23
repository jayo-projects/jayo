import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

dependencies {
    api(project(":jayo"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

kotlin {
    explicitApi = ExplicitApiMode.Disabled
}
