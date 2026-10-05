dependencies {
    api(project(":jsonpath-core"))
    api(libs.jakarta.json.api)

    testImplementation(testFixtures(project(":jsonpath-core")))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    // Any JSON-P implementation works; the tests use Eclipse Parsson.
    testRuntimeOnly(libs.parsson)
    testRuntimeOnly(libs.junit.platform.launcher)
}
