package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class SoftwarePublicQueryTest {

    @Test
    void queryFiltersPublishedAppsByDefault() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/apps").build());

        var query = new SoftwarePublicQuery(exchange);

        assertThat(query.toListOptions().toString()).contains("spec.published", "true");
    }

    @Test
    void querySupportsKeywordCategoryAndPagination() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("/apps?page=2&size=12&keyword=clean&category=system-tools")
            .build());

        var query = new SoftwarePublicQuery(exchange);

        assertThat(query.getPage()).isEqualTo(2);
        assertThat(query.getSize()).isEqualTo(12);
        assertThat(query.toListOptions().toString())
            .contains("spec.displayName", "clean", "spec.categoryName", "system-tools");
    }

    @Test
    void queryClampsPublicPageSize() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("/apps?page=1&size=999999")
            .build());

        var query = new SoftwarePublicQuery(exchange);

        assertThat(query.getSize()).isEqualTo(100);
    }

    @Test
    void queryRejectsInvalidPageSize() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("/apps?page=abc&size=bad")
            .build());

        var query = new SoftwarePublicQuery(exchange);

        org.assertj.core.api.Assertions.assertThatThrownBy(query::toPageRequest)
            .isInstanceOf(org.springframework.web.server.ServerWebInputException.class)
            .hasMessageContaining("Invalid public pagination");
    }
}
