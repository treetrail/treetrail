buildscript {
    dependencies {
        constraints {
            // Gradle 9.8.2 embeds Kotlin 2.4.10, whose build cache metadata can be deserialized unsafely
            // (GHSA-r937-wjx7-w2jp, fixed in 2.4.20). Build time only; remove when Gradle embeds 2.4.20 or later.
            classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        }
    }
}

plugins {
    `kotlin-dsl`
}

/** The artifact of a Gradle plugin from the version catalog, for applying it in the convention plugins. */
fun plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" }

dependencies {
    implementation(plugin(libs.plugins.spotless))
    implementation(plugin(libs.plugins.errorprone))
    implementation(plugin(libs.plugins.cyclonedx))
    implementation(plugin(libs.plugins.maven.publish))
    // The CycloneDX plugin pulls Jackson 2.22.1 onto the build class path (GHSA-p6pp-m3f8-5c89,
    // GHSA-7hhh-6rmp-j9qf, fixed in 2.22.3). Build time only; remove when the plugin ships a fixed one.
    implementation(platform(libs.jackson2.bom))
    constraints {
        // The CycloneDX plugin pulls Guava 33.7.1 onto the build class path (CVE in CompactHashMap and
        // MapMakerInternalMap deserialization, fixed in 33.7.2). Build time only; remove when the plugin ships a fixed one.
        implementation(libs.guava)
    }
}
