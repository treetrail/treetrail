plugins {
    id("treetrail.published-conventions")
}

description = "OpenAPI Overlay for Jackson 3 trees (tools.jackson.databind.JsonNode), on top of openapi-overlay."

treetrail {
    coverage(line = 0.98, branch = 0.95)
}

dependencies {
    api(project(":openapi-overlay"))
    api(libs.jackson3.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    // The module name ends in a digit on purpose (jackson2/jackson3, like Jackson's own jsr310).
    options.compilerArgs.add("-Xlint:-module")
}

// The fixtures of openapi-overlay, applied to Jackson 3 trees.
val fixtures = rootProject.layout.projectDirectory.dir("openapi-overlay/src/test/resources")
tasks.withType<Test>().configureEach {
    inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("fixtures")
    systemProperty("treetrail.overlayFixtures", fixtures.asFile.path)
}
