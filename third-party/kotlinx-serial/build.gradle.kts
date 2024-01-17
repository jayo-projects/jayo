plugins {
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":jayo"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings {
                optIn("kotlinx.serialization.json.internal.JsonFriendModuleApi")
            }
        }
    }
}
