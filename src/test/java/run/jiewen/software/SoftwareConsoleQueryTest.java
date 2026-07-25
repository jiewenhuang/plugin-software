package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class SoftwareConsoleQueryTest {

    @Test
    void querySupportsCategoryPlatformAndPublishedFilters() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("/apps?category=category-1&platform=macOS&published=true")
            .build());

        var query = new SoftwareConsoleQuery(exchange);

        assertThat(query.toListOptions().toString())
            .contains("spec.categoryName", "category-1", "spec.platform", "macOS",
                "spec.published", "true");
    }

    @Test
    void querySupportsDownloadAssetFilters() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("/download-assets?type=local&enabled=false&protection=protected")
            .build());

        var query = new SoftwareConsoleQuery(exchange);

        assertThat(query.toListOptions("spec.title").toString())
            .contains("spec.type", "local", "spec.enabled", "false",
                "spec.protected", "true");
    }
}
