description = "Tests for JsonModel implementations: runs the JSONPath Compliance Test Suite (RFC 9535) and the JsonModel contract against your model, as JUnit dynamic tests."

dependencies {
    api(project(":jsonpath-core"))
    // JUnit comes from the project that runs the tests: the dynamic tests work with JUnit 5 and 6, so the
    // testkit does not bring a version of its own (compileOnly is in neither the POM nor the Gradle metadata).
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
