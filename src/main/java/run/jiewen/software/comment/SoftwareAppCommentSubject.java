package run.jiewen.software.comment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.content.comment.CommentSubject;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.infra.ExternalLinkProcessor;
import run.jiewen.software.extension.SoftwareApp;

@Component
@RequiredArgsConstructor
class SoftwareAppCommentSubject implements CommentSubject<SoftwareApp> {

    private final ReactiveExtensionClient client;
    private final ExternalLinkProcessor externalLinkProcessor;
    private final GroupVersionKind gvk = GroupVersionKind.fromExtension(SoftwareApp.class);

    @Override
    public Mono<SoftwareApp> get(String name) {
        return client.fetch(SoftwareApp.class, name);
    }

    @Override
    public Mono<SubjectDisplay> getSubjectDisplay(String name) {
        return get(name).map(app -> {
            var spec = app.getSpec();
            var resourceName = firstText(metadataName(app), name);
            var title = firstText(spec == null ? null : spec.getDisplayName(), resourceName);
            var slug = firstText(spec == null ? null : spec.getSlug(), resourceName);
            var url = externalLinkProcessor.processLink("/soft/" + slug);
            return new SubjectDisplay(title, url, "软件");
        });
    }

    @Override
    public boolean supports(Ref ref) {
        Assert.notNull(ref, "Subject ref must not be null.");
        return Ref.groupKindEquals(ref, gvk);
    }

    private static String metadataName(SoftwareApp app) {
        var metadata = app.getMetadata();
        return metadata == null ? null : metadata.getName();
    }

    private static String firstText(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
