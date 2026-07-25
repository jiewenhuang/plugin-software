package run.jiewen.software.vo;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import run.halo.app.core.extension.content.Post;

/** Minimal public projection of a related Halo post. */
@Value
@Builder
public class SoftwareRelatedPostVo {

    String title;

    String slug;

    String cover;

    String excerpt;

    String permalink;

    Instant publishedAt;

    public static SoftwareRelatedPostVo from(Post post, String safeExcerpt) {
        var spec = post.getSpec();
        var status = post.getStatus();
        return SoftwareRelatedPostVo.builder()
            .title(spec == null ? null : spec.getTitle())
            .slug(spec == null ? null : spec.getSlug())
            .cover(spec == null ? null : spec.getCover())
            .excerpt(safeExcerpt)
            .permalink(status == null ? null : status.getPermalink())
            .publishedAt(spec == null ? null : spec.getPublishTime())
            .build();
    }
}
