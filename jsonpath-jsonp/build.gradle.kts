description = "Treetrail JSONPath (RFC 9535) on Jakarta JSON-P values (jakarta.json.JsonValue), with any JSON-P implementation."

dependencies {
    api(project(":jsonpath-core"))
    api(libs.jakarta.json.api)

    testImplementation(project(":jsonpath-model-testkit"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    // Any JSON-P implementation works; the tests use Eclipse Parsson.
    testRuntimeOnly(libs.parsson)
    testRuntimeOnly(libs.junit.platform.launcher)
}
