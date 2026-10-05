rootProject.name = "jsonpath"

include("jsonpath-core")
include("jsonpath-jackson2")
include("jsonpath-jackson3")
include("jsonpath-gson")
include("jsonpath-migration")
include("jsonpath-benchmarks")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
