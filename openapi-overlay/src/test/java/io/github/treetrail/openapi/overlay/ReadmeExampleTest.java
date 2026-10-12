package io.github.treetrail.openapi.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The overlay in the README's chapter on OpenAPI Overlays is valid and does what the comments say. */
class ReadmeExampleTest {

    @Test
    void theReadmeOverlayWorks() throws Exception {
        String readme = Files.readString(Path.of("../README.md"));
        String chapter = readme.substring(readme.indexOf("## OpenAPI Overlays"));
        int start = chapter.indexOf("```yaml\n") + "```yaml\n".length();
        Overlay overlay = Overlay.parse(chapter.substring(start, chapter.indexOf("```", start)));

        OverlayResult result = overlay.applyTo(OverlayDocuments.parse("""
                openapi: 3.1.0
                info: {title: Pets, version: 1.0.0, description: About pets}
                paths:
                  /pets:
                    get:
                      parameters: [{name: limit, in: query}]
                      responses: {'200': {description: OK}}
                  /internal:
                    get: {x-internal: true, responses: {'200': {description: OK}}}
                components:
                  schemas:
                    Pet: {properties: {description: {type: string}}}
                """));

        assertThat(result.document()).isEqualTo(OverlayDocuments.parse("""
                openapi: 3.1.0
                info: {title: Pets, version: 1.0.0, description: See the developer portal.}
                paths:
                  /pets:
                    get:
                      parameters: [{name: limit, in: query, schema: {type: string}}]
                      responses: {'200': {description: See the developer portal.}}
                  /internal: {}
                components:
                  schemas:
                    Pet: {properties: {description: {type: string}}}
                """));
        assertThat(result.warnings()).isEmpty();
    }
}
