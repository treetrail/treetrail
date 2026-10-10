// Every module, including the benchmarks: Java 17 bytecode from a JDK 25 toolchain, one formatting, Error Prone,
// reproducible javadoc.

import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val libs = versionCatalogs.named("libs")

group = "io.github.treetrail"
// Set by the release workflow from the Git tag (vX.Y.Z -> X.Y.Z).
version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-SNAPSHOT")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Compile with the JDK 25 toolchain, but stay usable on Java 17.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// One formatting for all Java sources, so that reviews need no style comments: `spotlessApply` formats,
// and `check` fails on unformatted code.
spotless {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Error Prone checks every compilation; with -Werror its warnings fail the build too.
dependencies {
    errorprone(libs.findLibrary("errorprone-core").get())
}
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
        // JMH's generated benchmark classes carry no @Generated annotation.
        excludedPaths = ".*/build/generated/.*"
    }
}

tasks.withType<Javadoc>().configureEach {
    // Each module only exports its API package, so javadoc only documents that package.
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
    // No generation date and a fixed language (not the system locale), so that the javadoc jar is
    // reproducible on every machine.
    (options as StandardJavadocDocletOptions).addBooleanOption("notimestamp", true)
    options.locale = "en"
    options.jFlags("-Duser.language=en", "-Duser.country=US")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
