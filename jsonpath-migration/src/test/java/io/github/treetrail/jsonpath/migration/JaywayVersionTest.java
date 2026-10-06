package io.github.treetrail.jsonpath.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Guards the test setup: each test task runs against the Jayway JsonPath version it claims. */
class JaywayVersionTest {

    @Test
    void runsAgainstTheExpectedJaywayVersion() {
        String expected = System.getProperty("jayway.version");
        String jar = com.jayway.jsonpath.JsonPath.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        assertThat(expected).as("system property jayway.version").isNotBlank();
        assertThat(jar).endsWith("json-path-" + expected + ".jar");
    }
}
