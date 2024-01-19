plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

val kotlinVersion by extra(property("kotlinVersion"))
val dokkaPluginVersion by extra(property("dokkaPluginVersion"))
val jmhPluginVersion by extra(property("jmhPluginVersion"))
val shadowPluginVersion by extra(property("shadowPluginVersion"))
val koverPluginVersion by extra(property("koverPluginVersion"))

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:$kotlinVersion"))
    
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation("org.jetbrains.kotlin:kotlin-serialization")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaPluginVersion")
    implementation("me.champeau.jmh:jmh-gradle-plugin:$jmhPluginVersion")
    implementation("com.github.johnrengelman:shadow:$shadowPluginVersion")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:$koverPluginVersion")
}

gradlePlugin {
    plugins {
        create("optionalDependenciesPlugin") {
            id = "jayo.build.optional-dependencies"
            implementationClass = "jayo.build.OptionalDependenciesPlugin"
        }
    }
}
