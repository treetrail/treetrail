description = "Treetrail JSONPath (RFC 9535) on Gson trees (com.google.gson.JsonElement)."

dependencies {
    api(project(":jsonpath-core"))
    api(libs.gson)

    testImplementation(testFixtures(project(":jsonpath-core")))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
