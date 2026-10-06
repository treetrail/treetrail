plugins {
    // Downloads the JDKs for the test runs on Java 17 and 21 when they are not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "treetrail"

include("jsonpath-core")
include("jsonpath-jackson2")
include("jsonpath-jackson3")
include("jsonpath-gson")
include("jsonpath-jsonp")
include("jsonpath-migration")
include("jsonpath-rewrite")
include("jsonpath-benchmarks")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
