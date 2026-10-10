plugins {
    id("treetrail.published-conventions")
}

description = "Treetrail JSONPath (RFC 9535) on Gson trees (com.google.gson.JsonElement)."

treetrail {
    coverage(line = 0.94, branch = 0.75)
}

dependencies {
    api(project(":jsonpath-core"))
    api(libs.gson)

    testImplementation(project(":jsonpath-model-testkit"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
