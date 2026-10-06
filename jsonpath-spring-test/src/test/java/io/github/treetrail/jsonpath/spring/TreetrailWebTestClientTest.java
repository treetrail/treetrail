package io.github.treetrail.jsonpath.spring;

import static io.github.treetrail.jsonpath.spring.TreetrailWebTestClient.jsonPath;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerResponse;

class TreetrailWebTestClientTest {

    private final WebTestClient client = WebTestClient.bindToRouterFunction(route(GET("/store"),
            request -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(TreetrailResultMatchersTest.STORE)))
            .build();

    @Test
    void checksValuesOfTheResponseBody() {
        client.get().uri("/store").exchange()
                .expectBody()
                .consumeWith(jsonPath("$.store.bicycle.color").value("red"))
                .consumeWith(jsonPath("$.store.book[?@.price < 10].title").values("Sayings", "Moby Dick"))
                .consumeWith(jsonPath("$.store.book[*]").hasSize(3))
                .consumeWith(jsonPath("$.store.owner").value("Zoë"))
                .consumeWith(jsonPath("$.store.book[2].isbn").exists())
                .consumeWith(jsonPath("$.store.music").doesNotExist());
    }

    @Test
    void explainsMismatches() {
        assertThatThrownBy(() -> client.get().uri("/store").exchange()
                .expectBody()
                .consumeWith(jsonPath("$.store.bicycle.price").value(400)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("JSON path \"$.store.bicycle.price\": expected one node with value 400 but found "
                        + "[399] at [$['store']['bicycle']['price']]");
    }
}
