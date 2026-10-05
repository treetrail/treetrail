plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

// Compile with the JDK 25 toolchain, but stay usable on Java 17.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

dependencies {
    // The core has no runtime dependencies on purpose.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    // Only used to read the compliance test suite.
    testImplementation(libs.jackson.databind)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.javadoc {
    // The module only exports the API package, so javadoc only documents that package.
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}
