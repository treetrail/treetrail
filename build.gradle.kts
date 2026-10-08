import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.cyclonedx.gradle.CyclonedxDirectTask

buildscript {
    dependencies {
        // The CycloneDX plugin pulls Jackson 2.22.1 onto the build class path (GHSA-p6pp-m3f8-5c89,
        // GHSA-7hhh-6rmp-j9qf, fixed in 2.22.3). Build time only; remove when the plugin ships a fixed one.
        classpath(platform(libs.jackson2.bom))
    }
}

plugins {
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.cyclonedx) apply false
}

/** The modules published to Maven Central; jsonpath-benchmarks is a build tool. */
val publishedModules = setOf(
    "jsonpath-core", "jsonpath-jackson2", "jsonpath-jackson3", "jsonpath-gson", "jsonpath-jsonp",
    "jsonpath-migration", "jsonpath-rewrite", "jsonpath-assertj", "jsonpath-spring-test",
)

/**
 * The build's timestamp in seconds since the epoch: SOURCE_DATE_EPOCH if set, otherwise the commit time of
 * HEAD. Used where an output must contain a date, so that two builds of the same commit are identical.
 */
val sourceDateEpoch: Provider<String> = providers.environmentVariable("SOURCE_DATE_EPOCH")
    .orElse(providers.exec { commandLine("git", "log", "-1", "--format=%ct") }.standardOutput.asText.map { it.trim() })

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
    }

    // Compile with the JDK 25 toolchain, but stay usable on Java 17.
    tasks.withType<JavaCompile>().configureEach {
        options.release = 17
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
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

    if (name in publishedModules) {
        // Every published jar (classes, sources, javadoc) carries the license and the notice. The javadoc jar
        // is the publishing plugin's own task type.
        val legalFiles = listOf("LICENSE", "NOTICE").map { rootProject.layout.projectDirectory.file(it) }
        tasks.withType<Jar>().configureEach {
            metaInf { from(legalFiles) }
        }
        tasks.withType<com.vanniktech.maven.publish.tasks.JavadocJar>().configureEach {
            metaInf { from(legalFiles) }
        }

        // A CycloneDX SBOM of the runtime dependencies, published next to the jar (classifier cyclonedx).
        apply(plugin = "org.cyclonedx.bom")
        val sbom = tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
            includeConfigs.set(listOf("runtimeClasspath"))
            includeBomSerialNumber.set(false)
            xmlOutput.unsetConvention()
            jsonOutput.set(layout.buildDirectory.file("sbom/${project.name}-cyclonedx.json"))

            // The plugin always writes the current time as metadata.timestamp and has no option for it.
            // Replace it with the commit time, so that the SBOM is reproducible like the jars.
            inputs.property("sourceDateEpoch", sourceDateEpoch)
            val sbomFile = jsonOutput
            val timestamp = sourceDateEpoch.map { java.time.Instant.ofEpochSecond(it.toLong()).toString() }
            doLast {
                val file = sbomFile.get().asFile
                val json = file.readText()
                val pattern = Regex("""("metadata" : \{\s*"timestamp" : ")[^"]*(")""")
                check(pattern.containsMatchIn(json)) { "No metadata.timestamp found in $file" }
                file.writeText(json.replaceFirst(pattern, "$1${timestamp.get()}$2"))
            }
        }

        apply(plugin = "com.vanniktech.maven.publish")
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                artifact(sbom.flatMap { it.jsonOutput }) {
                    classifier = "cyclonedx"
                    extension = "json"
                }
            }
        }
        extensions.configure<MavenPublishBaseExtension> {
            configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
            publishToMavenCentral()
            signAllPublications()
            pom {
                name.set(project.name)
                description.set(provider { project.description })
                url.set("https://github.com/treetrail/treetrail")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("christoph-sens")
                        name.set("Christoph Sens")
                        url.set("https://github.com/christoph-sens")
                    }
                }
                scm {
                    url.set("https://github.com/treetrail/treetrail")
                    connection.set("scm:git:https://github.com/treetrail/treetrail.git")
                    developerConnection.set("scm:git:ssh://git@github.com/treetrail/treetrail.git")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/treetrail/treetrail/issues")
                }
            }
        }
    }
}
