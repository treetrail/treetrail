plugins {
    id("treetrail.published-conventions")
}

description = "AssertJ assertions for JSON with Treetrail JSONPath (RFC 9535): assertThatJson(body).jsonPath(\"$.items[*].id\")."

treetrail {
    coverage(line = 0.89, branch = 0.74)
}

dependencies {
    api(project(":jsonpath-core"))
    api(libs.assertj)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
