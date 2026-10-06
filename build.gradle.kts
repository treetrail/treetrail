allprojects {
    group = "io.github.treetrail"
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

    // The library targets Java 17, so its tests also run on Java 17 and 21; `test` itself runs on 25.
    // `check` (and so `build`) includes these runs. The OpenRewrite recipe needs JDK 25 to parse Java 25
    // sources, and the benchmarks have no tests.
    if (name != "jsonpath-rewrite" && name != "jsonpath-benchmarks") {
        val javaToolchains = extensions.getByType<JavaToolchainService>()
        val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]
        for (version in listOf(17, 21)) {
            val testOnJava = tasks.register<Test>("testJava$version") {
                description = "Runs the unit tests on Java $version."
                group = "verification"
                testClassesDirs = testSourceSet.output.classesDirs
                classpath = testSourceSet.runtimeClasspath
                javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(version) }
                useJUnitPlatform {
                    // Like `test` in jsonpath-migration: the Jayway report is generated on request only.
                    excludeTags("report")
                }
            }
            tasks.named("check") { dependsOn(testOnJava) }
        }
    }
}
