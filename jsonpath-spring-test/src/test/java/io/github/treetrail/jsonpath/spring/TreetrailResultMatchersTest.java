package io.github.treetrail.jsonpath.spring;

import static io.github.treetrail.jsonpath.spring.TreetrailResultMatchers.jsonPath;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class TreetrailResultMatchersTest {

    static final String STORE = "{\"store\":{"
            + "\"book\":[{\"title\":\"Sayings\",\"price\":8.95},{\"title\":\"Sword\",\"price\":12.99},"
            + "{\"title\":\"Moby Dick\",\"price\":8.99,\"isbn\":null}],"
            + "\"bicycle\":{\"color\":\"red\",\"price\":399},\"owner\":\"Zoë\"}}";

    @RestController
    static class StoreController {
        @GetMapping(value = "/store", produces = MediaType.APPLICATION_JSON_VALUE)
        String store() {
            return STORE;
        }

        @GetMapping(value = "/broken", produces = MediaType.APPLICATION_JSON_VALUE)
        String broken() {
            return "{\"a\":";
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StoreController()).build();

    @Test
    void matchesValuesOfTheResponseBody() throws Exception {
        mockMvc.perform(get("/store"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store.bicycle.color").value("red"))
                .andExpect(jsonPath("$.store.bicycle.price").value(399L))
                .andExpect(jsonPath("$.store.book[0].price").value(8.95))
                .andExpect(jsonPath("$.store.bicycle").value(Map.of("price", 399, "color", "red")))
                .andExpect(jsonPath("$.store.book[?@.price < 10].title").values("Sayings", "Moby Dick"))
                .andExpect(jsonPath("$.store.book[*].title").hasSize(3))
                .andExpect(jsonPath("$.store.book[2].isbn").value(null))
                .andExpect(jsonPath("$.store.book[2].isbn").exists())
                .andExpect(jsonPath("$.store.book[0].isbn").doesNotExist())
                .andExpect(jsonPath("$.store.owner").value("Zoë"))
                .andExpect(jsonPath("$.store.book[*].price").values(List.of(8.95, 12.99, 8.99).toArray()));
    }

    @Test
    void explainsMismatchesWithValuesAndPaths() {
        assertThatThrownBy(() -> mockMvc.perform(get("/store")).andExpect(jsonPath("$..price").value(399)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("JSON path \"$..price\": expected one node with value 399 but found [")
                .hasMessageContaining("$['store']['bicycle']['price']");
        assertThatThrownBy(() -> mockMvc.perform(get("/store")).andExpect(jsonPath("$.store.bicycle.color").value("blue")))
                .hasMessage("JSON path \"$.store.bicycle.color\": expected one node with value \"blue\" but found "
                        + "[red] at [$['store']['bicycle']['color']]");
        assertThatThrownBy(() -> mockMvc.perform(get("/store")).andExpect(jsonPath("$.store.music").exists()))
                .hasMessage("JSON path \"$.store.music\": expected at least one node but found none");
        assertThatThrownBy(() -> mockMvc.perform(get("/store")).andExpect(jsonPath("$.store.book[*]").hasSize(2)))
                .hasMessageStartingWith("JSON path \"$.store.book[*]\": expected 2 node(s) but found [");
    }

    @Test
    void reportsBodiesThatAreNotJson() {
        assertThatThrownBy(() -> mockMvc.perform(get("/broken")).andExpect(jsonPath("$.a").exists()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the response body is not valid JSON");
    }

    @Test
    void rejectsJaywayOnlySyntaxWhereTheMatcherIsWritten() {
        assertThatThrownBy(() -> jsonPath("$.store.book.length()"))
                .isInstanceOf(JsonPathSyntaxException.class);
    }
}
