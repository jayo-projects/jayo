//val centralUsername = if (project.hasProperty("centralUsername")) {
//    project.property("centralUsername") as String?
//} else {
//    System.getenv("CENTRAL_USERNAME")
//}
//val centralPassword = if (project.hasProperty("centralPassword")) {
//    project.property("centralPassword") as String?
//} else {
//    System.getenv("CENTRAL_PASSWORD")
//}

plugins {
    `maven-publish`
    signing
    id("net.researchgate.release")
}

subprojects {
    apply(plugin = "jayo-conventions")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    // --------------- publishing ---------------

    publishing {
        repositories {
            maven {
                url = uri(layout.buildDirectory.dir("repos/releases"))

//                if (project.version.toString().endsWith("SNAPSHOT")) {
//                    url = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
//                } else {
//                    url = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
//                }
//                credentials {
//                    username = centralUsername
//                    password = centralPassword
//                }
            }
        }

        publications {
            create<MavenPublication>("mavenJava") {
                pom {
                    name.set(project.name)
                    description.set("Jayo is a synchronous I/O library for the JVM")
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

// when version changes :
// -> execute ./gradlew wrapper, then remove .gradle directory, then execute ./gradlew wrapper again
tasks.wrapper {
    gradleVersion = "8.5"
    distributionType = Wrapper.DistributionType.ALL
}
