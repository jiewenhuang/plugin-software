package run.jiewen.software.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.jiewen.software.SoftwareDomainException;
import run.jiewen.software.api.SoftwareResourceKind;
import run.jiewen.software.extension.*;
import run.jiewen.software.finders.SoftwareConsoleService;
import tools.jackson.databind.json.JsonMapper;

class SoftwarePreflightServiceTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final SoftwareConsoleService domain = mock(SoftwareConsoleService.class);
    private final SoftwarePreflightService service = new SoftwarePreflightService(client, domain);

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setup() {
        doReturn(Mono.just(new ListResult<>(1, 20, 0, List.of())))
            .when(client).listBy(any(Class.class), any(ListOptions.class), any(PageRequest.class));
        when(domain.validateResourceState(any())).thenReturn(Mono.empty());
    }

    @Test
    void deletionSeparatesBlockingAndRetainedReferencesAndBoundsNames() {
        var app = app(); when(domain.getResource(SoftwareResourceKind.APP, "app")).thenReturn(Mono.just(app));
        var version = new SoftwareVersion(); metadata(version, "version");
        when(client.listBy(eq(SoftwareVersion.class), any(), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 20, 25, List.of(version))));
        var topic = new SoftwareTopic(); metadata(topic, "topic");
        when(client.listBy(eq(SoftwareTopic.class), any(), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(topic))));
        var report = service.deletion(SoftwareResourceKind.APP, "app").block();
        assertThat(report.allowed()).isFalse();
        assertThat(report.dependencies().getFirst().count()).isEqualTo(25);
        assertThat(report.dependencies().getFirst().names()).containsExactly("version");
        assertThat(report.dependencies().getFirst().truncated()).isTrue();
        assertThat(report.issues()).extracting(SoftwarePreflightService.Issue::code)
            .contains("RESOURCE_IN_USE", "REFERENCES_RETAINED");
        verify(client, never()).delete(any()); verify(client, never()).update(any());
    }

    @Test
    void noReleasedVersionWarnsWithoutInventingPublicationProhibition() {
        when(domain.getResource(SoftwareResourceKind.APP, "app")).thenReturn(Mono.just(app()));
        var report = service.publication(SoftwareResourceKind.APP, "app").block();
        assertThat(report.allowed()).isTrue(); assertThat(report.ready()).isFalse();
        assertThat(report.issues()).extracting(SoftwarePreflightService.Issue::code).containsExactly("NO_RELEASED_VERSION");
    }

    @Test
    void invalidStoredReferencesBlockPublication() {
        when(domain.getResource(SoftwareResourceKind.APP, "app")).thenReturn(Mono.just(app()));
        when(domain.validateResourceState(any())).thenReturn(Mono.error(new SoftwareDomainException("REFERENCE_INVALID", "missing tag")));
        var report = service.publication(SoftwareResourceKind.APP, "app").block();
        assertThat(report.allowed()).isFalse();
        assertThat(report.issues()).extracting(SoftwarePreflightService.Issue::code).contains("REFERENCE_INVALID");
    }

    @Test
    void latestVersionPrimaryDownloadCheckedWithoutLeakingSecrets() {
        when(domain.getResource(SoftwareResourceKind.APP, "app")).thenReturn(Mono.just(app()));
        var version = new SoftwareVersion(); metadata(version, "version");
        when(client.listBy(eq(SoftwareVersion.class), any(), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(version))));
        var asset = new DownloadAsset(); metadata(asset, "asset");
        var spec = new DownloadAsset.Spec();spec.setUrl("https://example.test/private-token.zip");
        spec.setExtractionCode("hidden-code");spec.setPasswordSecretName("hidden-reference");asset.setSpec(spec);
        when(client.listBy(eq(DownloadAsset.class), any(), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 200, 201, List.of(asset))));
        when(client.fetch(Secret.class, "hidden-reference")).thenReturn(Mono.empty());
        var report = service.publication(SoftwareResourceKind.APP, "app").block();
        assertThat(report.ready()).isFalse();
        assertThat(report.issues()).extracting(SoftwarePreflightService.Issue::code)
            .contains("CHECK_INCOMPLETE", "PRIMARY_DOWNLOAD_UNUSABLE");
        assertThat(JsonMapper.shared().writeValueAsString(report)).doesNotContain("private-token", "hidden-code", "hidden-reference");
        spec.setPasswordSecretName(null);
        when(client.listBy(eq(DownloadAsset.class), any(), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 200, 1, List.of(asset))));
        assertThat(service.publication(SoftwareResourceKind.APP, "app").block().ready()).isTrue();
    }

    @Test
    void topicWarnsWhenItemsAreUnpublishedAndDeleteKeepsSoftware() {
        var topic = new SoftwareTopic(); metadata(topic, "topic");
        var spec = new SoftwareTopic.Spec(); var item = new SoftwareTopic.Item();item.setAppName("app");
        spec.setItems(List.of(item));topic.setSpec(spec);
        when(domain.getResource(SoftwareResourceKind.TOPIC, "topic")).thenReturn(Mono.just(topic));
        when(client.fetch(SoftwareApp.class, "app")).thenReturn(Mono.just(app()));
        var report = service.publication(SoftwareResourceKind.TOPIC, "topic").block();
        assertThat(report.allowed()).isTrue(); assertThat(report.ready()).isFalse();
        assertThat(report.issues()).extracting(SoftwarePreflightService.Issue::code).contains("HIDDEN_TOPIC_ITEMS");
        var delete = service.deletion(SoftwareResourceKind.TOPIC, "topic").block();
        assertThat(delete.allowed()).isTrue();
        assertThat(delete.issues()).extracting(SoftwarePreflightService.Issue::code).containsExactly("SOFTWARE_RETAINED");
    }

    @Test
    void legacyNullTopicItemsProduceAWarningInsteadOfFailingThePreflight() {
        var topic = new SoftwareTopic(); metadata(topic, "topic");
        var spec = new SoftwareTopic.Spec();
        var item = new SoftwareTopic.Item(); item.setAppName("app");
        spec.setItems(java.util.Arrays.asList(null, item)); topic.setSpec(spec);
        when(domain.getResource(SoftwareResourceKind.TOPIC, "topic")).thenReturn(Mono.just(topic));
        var app = app(); app.getSpec().setPublished(true);
        when(client.fetch(SoftwareApp.class, "app")).thenReturn(Mono.just(app));

        var report = service.publication(SoftwareResourceKind.TOPIC, "topic").block();
        assertThat(report.allowed()).isTrue();
        assertThat(report.ready()).isFalse();
        assertThat(report.issues()).extracting(SoftwarePreflightService.Issue::code)
            .containsExactly("HIDDEN_TOPIC_ITEMS");
        verify(client, never()).update(any());
    }

    private static SoftwareApp app() {
        var app = new SoftwareApp(); metadata(app, "app");
        var spec = new SoftwareApp.Spec();spec.setSlug("app");spec.setDisplayName("App");spec.setPublished(false);app.setSpec(spec);
        return app;
    }
    private static void metadata(AbstractExtension value, String name) {
        var metadata = new Metadata();metadata.setName(name);metadata.setVersion(1L);value.setMetadata(metadata);
    }
}
