import org.gradle.tooling.GradleConnector

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.release)

    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.mrjar) apply false
    alias(libs.plugins.jmh) apply false
    alias(libs.plugins.shadow) apply false
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    // --------------- publishing ---------------

    publishing {
        repositories {
            maven {
                url = uri(layout.buildDirectory.dir("repos/releases"))
            }
        }

        publications {
            create<MavenPublication>("mavenJava") {
                pom {
                    name.set(project.name)
                    description.set("Jayo is a fast synchronous I/O and TLS library for the JVM")
                    url.set("https://github.com/jayo-projects/jayo")

                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            name.set("pull-vert")
                            url.set("https://github.com/pull-vert")
                        }
                    }

                    scm {
                        connection.set("scm:git:https://github.com/jayo-projects/jayo")
                        developerConnection.set("scm:git:git://github.com/jayo-projects/jayo.git")
                        url.set("https://github.com/jayo-projects/jayo.git")
                    }
                }
            }
        }
    }

    signing {
        // Require signing.keyId, signing.password and signing.secretKeyRingFile
        sign(publishing.publications)
    }
}

// workaround : https://github.com/researchgate/gradle-release/issues/304#issuecomment-1083692649
configure(listOf(tasks.release, tasks.runBuildTasks)) {
    configure {
        actions.clear()
        doLast {
            GradleConnector
                .newConnector()
                .forProjectDirectory(layout.projectDirectory.asFile)
                .connect()
                .use { projectConnection ->
                    val buildLauncher = projectConnection
                        .newBuild()
                        .forTasks(*tasks.toTypedArray())
                        .setStandardInput(System.`in`)
                        .setStandardOutput(System.out)
                        .setStandardError(System.err)
                    gradle.startParameter.excludedTaskNames.forEach {
                        buildLauncher.addArguments("-x", it)
                    }
                    buildLauncher.run()
                }
        }
    }
}

// when the Gradle version changes:
// -> execute ./gradlew wrapper, then remove .gradle directory, then execute ./gradlew wrapper again
tasks.wrapper {
    gradleVersion = "8.14.2"
    distributionType = Wrapper.DistributionType.ALL
}
