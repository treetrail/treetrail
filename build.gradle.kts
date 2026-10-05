allprojects {
    group = "com.christoph-sens"
    // Set by the release workflow from the Git tag (vX.Y.Z -> X.Y.Z).
    version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-SNAPSHOT")
}

// Shared setup for all library modules.
subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
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

    tasks.withType<Javadoc>().configureEach {
        // Each module only exports its API package, so javadoc only documents that package.
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
