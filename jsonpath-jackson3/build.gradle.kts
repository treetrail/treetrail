dependencies {
    api(project(":jsonpath-core"))
    api(libs.jackson3.databind)

    testImplementation(testFixtures(project(":jsonpath-core")))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    // The module name ends in a digit on purpose (jackson2/jackson3, like Jackson's own jsr310).
    options.compilerArgs.add("-Xlint:-module")
}
