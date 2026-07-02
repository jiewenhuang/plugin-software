package run.jiewen.software.ai;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class SoftwareHtmlSanitizer {

    private static final Safelist SAFE_FRAGMENT = Safelist.none()
        .addTags(
            "p", "br", "hr",
            "h2", "h3", "h4",
            "ul", "ol", "li", "dl", "dt", "dd",
            "strong", "em", "b", "i", "u", "s", "del", "mark", "span",
            "blockquote", "pre", "code",
            "table", "thead", "tbody", "tr", "th", "td",
            "a")
        .addAttributes("a", "href", "title", "target")
        .addAttributes("td", "colspan", "rowspan")
        .addAttributes("th", "colspan", "rowspan")
        .addProtocols("a", "href", "http", "https", "mailto")
        .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

    public String sanitize(String html) {
        if (!StringUtils.isNotBlank(html)) {
            return "";
        }
        var outputSettings = new Document.OutputSettings().prettyPrint(false);
        var cleaned = Jsoup.clean(html, "", SAFE_FRAGMENT, outputSettings);
        var fragment = Jsoup.parseBodyFragment(cleaned);
        fragment.outputSettings(outputSettings);
        fragment.select("a:not([href])").unwrap();
        return fragment.body().html();
    }
}
