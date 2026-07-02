package run.jiewen.software.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SoftwareHtmlSanitizerTest {

    private final SoftwareHtmlSanitizer sanitizer = new SoftwareHtmlSanitizer();

    @Test
    void keepsRichContentTags() {
        var html = "<p onclick=\"alert(1)\"><strong>Clean</strong><script>alert(1)</script></p>"
            + "<img src=x onerror=alert(1)><h2>亮点</h2><ul><li><em>Fast</em></li></ul>"
            + "<blockquote>适合批量处理。</blockquote>"
            + "<pre><code>tiny --compress</code></pre>"
            + "<table><thead><tr><th>版本</th></tr></thead>"
            + "<tbody><tr><td colspan=\"2\">1.0</td></tr></tbody></table>";

        assertThat(sanitizer.sanitize(html))
            .contains("<p><strong>Clean</strong></p>")
            .contains("<h2>亮点</h2>")
            .contains("<ul><li><em>Fast</em></li></ul>")
            .contains("<blockquote>适合批量处理。</blockquote>")
            .contains("<pre><code>tiny --compress</code></pre>")
            .contains("<table><thead><tr><th>版本</th></tr></thead>")
            .contains("<td colspan=\"2\">1.0</td>")
            .doesNotContain("<script", "<img", "onclick", "onerror");
    }

    @Test
    void keepsSafeLinksAndDropsUnsafeAttributes() {
        var html = "<p><a href=\"https://example.com\" target=\"_blank\" onclick=\"alert(1)\">官网</a>"
            + "<a href=\"javascript:alert(1)\">bad</a>"
            + "<span style=\"color:red\">text</span></p>";

        assertThat(sanitizer.sanitize(html))
            .contains("<a")
            .contains("href=\"https://example.com\"")
            .contains("target=\"_blank\"")
            .contains("rel=\"nofollow noopener noreferrer\"")
            .contains(">官网</a>bad<span>text</span>")
            .doesNotContain("javascript:", "onclick", "style=");
    }
}
