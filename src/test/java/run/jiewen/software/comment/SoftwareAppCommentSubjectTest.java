package run.jiewen.software.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.infra.ExternalLinkProcessor;
import run.jiewen.software.extension.SoftwareApp;

@ExtendWith(MockitoExtension.class)
class SoftwareAppCommentSubjectTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ExternalLinkProcessor externalLinkProcessor;

    @Test
    void supportsSoftwareAppSubjectRefs() {
        var subject = subject();

        assertThat(subject.supports(ref("software.jiewen.run", "v1alpha1", "SoftwareApp")))
            .isTrue();
        assertThat(subject.supports(ref("software.jiewen.run", null, "SoftwareApp"))).isTrue();
        assertThat(subject.supports(ref("content.halo.run", "v1alpha1", "Post"))).isFalse();
        assertThat(subject.supports(ref("software.jiewen.run", "v1alpha1", "SoftwareTopic")))
            .isFalse();
    }

    @Test
    void getsSoftwareAppByResourceName() {
        var app = app("software-generated-name", "cleanmymac-x", "CleanMyMac X");
        var subject = subject();
        when(client.fetch(SoftwareApp.class, "software-generated-name")).thenReturn(Mono.just(app));

        StepVerifier.create(subject.get("software-generated-name"))
            .expectNext(app)
            .verifyComplete();

        verify(client).fetch(SoftwareApp.class, "software-generated-name");
    }

    @Test
    void displaysSoftwareAppTitleAndPublicUrl() {
        var subject = subject();
        when(client.fetch(SoftwareApp.class, "software-generated-name"))
            .thenReturn(Mono.just(app("software-generated-name", "cleanmymac-x", "CleanMyMac X")));
        when(externalLinkProcessor.processLink("/soft/cleanmymac-x"))
            .thenReturn("https://macwk.example/soft/cleanmymac-x");

        StepVerifier.create(subject.getSubjectDisplay("software-generated-name"))
            .assertNext(display -> {
                assertThat(display.title()).isEqualTo("CleanMyMac X");
                assertThat(display.url()).isEqualTo("https://macwk.example/soft/cleanmymac-x");
                assertThat(display.kindName()).isEqualTo("软件");
            })
            .verifyComplete();
    }

    @Test
    void fallsBackToResourceNameWhenDisplayFieldsAreMissing() {
        var subject = subject();
        when(client.fetch(SoftwareApp.class, "software-generated-name"))
            .thenReturn(Mono.just(app("software-generated-name", null, null)));
        when(externalLinkProcessor.processLink("/soft/software-generated-name"))
            .thenReturn("https://macwk.example/soft/software-generated-name");

        StepVerifier.create(subject.getSubjectDisplay("software-generated-name"))
            .assertNext(display -> {
                assertThat(display.title()).isEqualTo("software-generated-name");
                assertThat(display.url()).isEqualTo("https://macwk.example/soft/software-generated-name");
                assertThat(display.kindName()).isEqualTo("软件");
            })
            .verifyComplete();
    }

    private SoftwareAppCommentSubject subject() {
        return new SoftwareAppCommentSubject(client, externalLinkProcessor);
    }

    private static Ref ref(String group, String version, String kind) {
        var ref = new Ref();
        ref.setGroup(group);
        ref.setVersion(version);
        ref.setKind(kind);
        ref.setName("software-generated-name");
        return ref;
    }

    private static SoftwareApp app(String name, String slug, String displayName) {
        var metadata = new Metadata();
        metadata.setName(name);

        var spec = new SoftwareApp.Spec();
        spec.setSlug(slug);
        spec.setDisplayName(displayName);

        var app = new SoftwareApp();
        app.setMetadata(metadata);
        app.setSpec(spec);
        return app;
    }
}
