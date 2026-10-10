plugins {
    id("treetrail.published-conventions")
}

description = "Treetrail JSONPath (RFC 9535) on Jackson 2 trees (com.fasterxml.jackson.databind.JsonNode)."

treetrail {
    coverage(line = 0.90, branch = 0.87)
}

dependencies {
    api(project(":jsonpath-core"))
    api(libs.jackson2.databind)

    testImplementation(project(":jsonpath-model-testkit"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    // The module name ends in a digit on purpose (jackson2/jackson3, like Jackson's own jsr310).
    options.compilerArgs.add("-Xlint:-module")
}
