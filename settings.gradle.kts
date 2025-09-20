pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

rootProject.name = "jayo-root"

include("benchmarks")

include(":jayo")
include(":third-party:jayo-3p-kotlinx-serialization")
include(":samples")
include(":jayo-scheduler")

project(":jayo").projectDir = file("./core")
project(":third-party:jayo-3p-kotlinx-serialization").projectDir = file("./third-party/kotlinx-serial")
project(":jayo-scheduler").projectDir = file("./scheduler")
