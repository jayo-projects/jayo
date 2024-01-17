val ossrhUsername = if (project.hasProperty("ossrhUsername")) {
    project.property("ossrhUsername") as String?
} else {
    System.getenv("OSSRH_USERNAME")
}
val ossrhPassword = if (project.hasProperty("ossrhPassword")) {
    project.property("ossrhPassword") as String?
} else {
    System.getenv("OSSRH_PASSWORD")
}

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
                if (project.version.toString().endsWith("SNAPSHOT")) {
                    setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                } else {
                    setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                }

                credentials {
                    username = ossrhUsername
                    password = ossrhPassword
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
