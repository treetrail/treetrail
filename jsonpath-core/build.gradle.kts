plugins {
    `java-test-fixtures`
}

dependencies {
    // The core has no runtime dependencies on purpose.

    // Test fixtures: the Compliance Test Suite runner, shared with the adapter modules.
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter.api)
    testFixturesImplementation(libs.assertj)
    // Only used to read the compliance test suite.
    testFixturesImplementation(libs.jackson2.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.jackson2.databind)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The test fixtures are for this build only and are never published.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
