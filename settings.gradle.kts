pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "jayo-root"

include("benchmarks")

include(":jayo")
include(":third-party:jayo-3p-kotlinx-serialization")
include(":samples")

project(":jayo").projectDir = file("./core")
project(":third-party:jayo-3p-kotlinx-serialization").projectDir = file("./third-party/kotlinx-serial")
