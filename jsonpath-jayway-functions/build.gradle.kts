plugins {
    id("treetrail.published-conventions")
}

description = "Jayway JsonPath's aggregate functions min, max, sum, avg and stddev as Treetrail function extensions. Not part of RFC 9535."

treetrail {
    coverage(line = 0.99, branch = 0.99)
}

dependencies {
    api(project(":jsonpath-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
