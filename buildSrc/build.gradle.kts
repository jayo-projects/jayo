plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

val kotlinVersion by extra(project.property("kotlinVersion"))
val dokkaPluginVersion by extra(project.property("dokkaPluginVersion"))
val jmhPluginVersion by extra(project.property("jmhPluginVersion"))
val shadowPluginVersion by extra(project.property("shadowPluginVersion"))
val koverPluginVersion by extra(project.property("koverPluginVersion"))

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:$kotlinVersion"))
    
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
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
