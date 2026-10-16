package run.jiewen.software.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.mcpserver.api.McpToolDefinition;
import run.halo.mcpserver.api.McpToolInvocation;
import run.halo.mcpserver.api.McpToolResult;
import run.jiewen.software.SoftwareOptions;
import run.jiewen.software.operations.SoftwareCreationService;
import run.jiewen.software.operations.SoftwarePreflightService;
import run.jiewen.software.api.SoftwareMutation;
import run.jiewen.software.api.SoftwareResourceKind;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.finders.SoftwareConsoleService;
import run.jiewen.software.vo.DownloadAssetStatsVo;
import run.jiewen.software.vo.SoftwareConsoleStatsVo;
import tools.jackson.databind.json.JsonMapper;

class SoftwareMcpToolProviderTest {
    private final SoftwareConsoleService service = mock(SoftwareConsoleService.class);
    private final SoftwareCreationService creation = mock(SoftwareCreationService.class);
    private final SoftwarePreflightService preflight = mock(SoftwarePreflightService.class);
    private final ReactiveSettingFetcher settings = mock(ReactiveSettingFetcher.class);
    private final AtomicReference<SoftwareMcpAccessPolicy.Options> options =
        new AtomicReference<>(new SoftwareMcpAccessPolicy.Options(true, false));
    private SoftwareMcpToolProvider provider;

    @BeforeEach
    void setup() {
        when(settings.fetch("mcp", SoftwareMcpAccessPolicy.Options.class))
            .thenAnswer(call -> Mono.just(options.get()));
        when(settings.fetch(SoftwareOptions.GROUP, SoftwareOptions.class)).thenReturn(Mono.just(new SoftwareOptions()));
        provider = new SoftwareMcpToolProvider(service, settings, creation, preflight);
    }

    @Test
    void catalogHas43BaseAndTwoOptionalToolsWithUniqueLocalNamesAndSchemas() {
        assertThat(provider.tools().collectList().block()).hasSize(43);
        options.set(new SoftwareMcpAccessPolicy.Options(true, true));
        var tools = provider.tools().collectList().block();
        assertThat(tools).hasSize(45);
        assertThat(tools.stream().map(McpToolDefinition::name).distinct().count()).isEqualTo(45);
        for (var tool : tools) {
            assertThat(tool.name()).matches("[a-z][a-z0-9_]{0,62}");
            assertThat(tool.inputSchema()).containsEntry("type", "object").containsEntry("additionalProperties", false);
            assertThat(tool.outputSchema()).containsEntry("type", "object");
            assertThat(tool.permission()).isNotNull();
            assertThat(tool.handler()).isNotNull();
        }
    }

    @Test
    void actualHandlersForEveryToolReturnTheirDeclaredOutputShapeAndNoOrdinarySecretLeak() {
        options.set(new SoftwareMcpAccessPolicy.Options(true, true));
        when(service.getAppStats()).thenReturn(Mono.just(SoftwareConsoleStatsVo.builder()
            .total(1).published(0).drafts(1).downloads(0).platforms(List.of()).build()));
        when(service.getDownloadAssetStats()).thenReturn(Mono.just(DownloadAssetStatsVo.builder().build()));
        when(creation.status(any(), anyString())).thenReturn(Mono.just(new SoftwareCreationService.Receipt(
            "00000000-0000-0000-0000-000000000001", "app", "resource", "succeeded", 7L, null, true)));
        for (var kind : SoftwareResourceKind.values()) {
            var report = new SoftwarePreflightService.Report("resource", kind.singular(), 7L, "a".repeat(64),
                "delete", true, true, "2026-10-16T00:00:00Z", List.of(), List.of());
            when(preflight.deletion(kind, "resource")).thenReturn(Mono.just(report));
            when(preflight.publication(kind, "resource")).thenReturn(Mono.just(report));
            var resource = fixture(kind);
            when(service.listResources(eq(kind), any())).thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(resource))));
            when(service.getResource(kind, "resource")).thenReturn(Mono.just(resource));
            when(service.createResource(eq(kind), anyMap())).thenReturn(Mono.just(resource));
            when(service.patchResource(eq(kind), eq("resource"), any())).thenReturn(Mono.just(resource));
            when(service.deleteResource(kind, "resource", 7L)).thenReturn(Mono.just(resource));
        }
        for (var tool : provider.tools().collectList().block()) {
            var args = validArguments(tool.name());
            var result = invoke(tool, args).block();
            assertThat(result.error()).as(tool.name()).isFalse();
            SoftwareMcpInput.validateValue(tool.outputSchema(), result.structuredContent(), "result");
            String json = JsonMapper.shared().writeValueAsString(result);
            if (!tool.name().equals("get_download_asset_secrets")) {
                assertThat(json).as(tool.name()).doesNotContain("private.zip", "extraction-secret", "storage-secret", "password-secret");
            } else {
                assertThat(json).contains("private.zip", "extraction-secret");
                assertThat(result.textContent()).doesNotContain("private.zip");
            }
            assertThat(json).doesNotContain("annotation-secret");
        }
    }

    @Test
    void rejectsUnknownFieldsInvalidValuesAndOversizedInputBeforeServiceCalls() {
        var get = tool("get_app");
        for (var args : List.of(Map.<String, Object>of(), Map.<String, Object>of("name", "resource", "extra", "value"),
            Map.<String, Object>of("name", " "))) {
            assertError(invoke(get, args).block(), "INVALID_ARGUMENT");
        }
        for (var args : List.of(Map.<String, Object>of("page", 0), Map.<String, Object>of("size", 101),
            Map.<String, Object>of("page", 1.5), Map.<String, Object>of("sortBy", "spec.secret"),
            Map.<String, Object>of("fieldSelector", "spec.published=true"))) {
            assertError(invoke(tool("list_apps"), args).block(), "INVALID_ARGUMENT");
        }
        var secrets = Map.<String, Object>of("spec", Map.of("appName", "app", "versionName", "version",
            "title", "Download", "url", "https://example.com/private.zip"));
        assertError(invoke(tool("create_download_asset"), secrets).block(), "INVALID_ARGUMENT");
        var args = new LinkedHashMap<String, Object>();
        args.put("name", "resource"); args.put("expectedVersion", 7);
        var patch = new LinkedHashMap<String, Object>(); patch.put("summary", null);
        args.put("patch", patch);
        assertError(invoke(tool("update_app"), args).block(), "INVALID_ARGUMENT");
        args.put("patch", Map.of("description", "文".repeat(100000)));
        assertError(invoke(tool("update_app"), args).block(), "INVALID_ARGUMENT");
        verifyNoInteractions(service);
    }

    @Test
    void requiresAuthenticationAndRechecksSettingsAfterToolsWereListed() {
        var read = tool("get_app");
        assertError(read.handler().execute(new McpToolInvocation("get_app", Map.of("name", "resource"))).block(), "FORBIDDEN");
        options.set(new SoftwareMcpAccessPolicy.Options(true, true));
        var sensitive = tool("get_download_asset_secrets");
        options.set(new SoftwareMcpAccessPolicy.Options(true, false));
        assertError(invoke(sensitive, Map.of("name", "resource")).block(), "FORBIDDEN");
        options.set(new SoftwareMcpAccessPolicy.Options(false, true));
        assertThat(provider.tools().collectList().block()).isEmpty();
        assertError(invoke(read, Map.of("name", "resource")).block(), "FORBIDDEN");
        verifyNoInteractions(service);
    }

    @Test
    void absentSettingsHaveSafeDefaultsAndReadFailuresCloseOnlyMcp() {
        when(settings.fetch("mcp", SoftwareMcpAccessPolicy.Options.class)).thenReturn(Mono.empty());
        assertThat(provider.tools().collectList().block()).hasSize(43);
        when(settings.fetch("mcp", SoftwareMcpAccessPolicy.Options.class)).thenReturn(Mono.error(new IllegalStateException("hidden")));
        assertThat(provider.tools().collectList().block()).isEmpty();
        verifyNoInteractions(service);
    }

    @Test
    void distinguishesReadTimeoutFromUnknownWriteOutcome() {
        when(service.getResource(SoftwareResourceKind.APP, "resource")).thenReturn(Mono.never());
        when(service.patchResource(eq(SoftwareResourceKind.APP), eq("resource"), any())).thenReturn(Mono.never());
        var get = tool("get_app");
        var update = tool("update_app");
        StepVerifier.withVirtualTime(() -> invoke(get, Map.of("name", "resource")))
            .thenAwait(Duration.ofSeconds(5)).assertNext(result -> assertError(result, "TIMEOUT")).verifyComplete();
        StepVerifier.withVirtualTime(() -> invoke(update, Map.of("name", "resource", "expectedVersion", 7,
                "patch", Map.of("summary", "changed"))))
            .thenAwait(Duration.ofSeconds(5)).assertNext(result -> assertError(result, "WRITE_OUTCOME_UNKNOWN")).verifyComplete();
    }

    @Test
    void oversizedReadFailsButSuccessfulWriteReturnsAReceiptAndUnknownErrorsAreRedacted() {
        var app = (SoftwareApp) fixture(SoftwareResourceKind.APP);
        app.getSpec().setDescription("x".repeat(600000));
        when(service.getResource(SoftwareResourceKind.APP, "resource")).thenReturn(Mono.just(app));
        when(service.patchResource(eq(SoftwareResourceKind.APP), eq("resource"), any())).thenReturn(Mono.just(app));
        assertError(invoke(tool("get_app"), Map.of("name", "resource")).block(), "RESULT_TOO_LARGE");
        var result = invoke(tool("update_app"), Map.of("name", "resource", "expectedVersion", 7,
            "patch", Map.of("summary", "changed"))).block();
        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("representationOmitted", true);
        SoftwareMcpInput.validateValue(tool("update_app").outputSchema(), result.structuredContent(), "result");
        when(service.getResource(SoftwareResourceKind.APP, "resource"))
            .thenReturn(Mono.error(new IllegalStateException("password-secret")));
        result = invoke(tool("get_app"), Map.of("name", "resource")).block();
        assertError(result, "INTERNAL");
        assertThat(JsonMapper.shared().writeValueAsString(result)).doesNotContain("password-secret");
    }

    @Test
    void keyedCreateAndReplayReturnValidReceiptsWithoutRawInput() {
        String id = "00000000-0000-0000-0000-000000000001";
        var resource = fixture(SoftwareResourceKind.APP);
        var receipt = new SoftwareCreationService.Receipt(id, "app", "resource", "succeeded", 7L, null, false);
        when(creation.create(eq(SoftwareResourceKind.APP), anyMap(), eq(id)))
            .thenReturn(Mono.just(new SoftwareCreationService.Outcome(resource, receipt)),
                Mono.just(new SoftwareCreationService.Outcome(null,
                    new SoftwareCreationService.Receipt(id, "app", "resource", "succeeded", 7L, null, true))));
        var tool = tool("create_app");
        for (int i = 0; i < 2; i++) {
            var result = invoke(tool, Map.of("spec", requiredSpec(SoftwareResourceKind.APP), "requestId", id)).block();
            assertThat(result.error()).isFalse();
            SoftwareMcpInput.validateValue(tool.outputSchema(), result.structuredContent(), "result");
            assertThat(SoftwareMcpSchemas.cast(result.structuredContent().get("operation"))).containsEntry("replayed", i == 1);
        }
        verify(service, never()).createResource(any(), anyMap());
    }

    @Test
    void editRevisionNeverAuthorizesSecretWritesOrBypassesVersionRequirement() {
        assertError(invoke(tool("update_app"), Map.of("name", "resource", "expectedEditRevision", "a".repeat(64),
            "patch", Map.of("summary", "change"))).block(), "INVALID_ARGUMENT");
        options.set(new SoftwareMcpAccessPolicy.Options(true, true));
        assertError(invoke(tool("update_download_asset_secrets"), Map.of("name", "resource", "expectedVersion", 7,
            "expectedEditRevision", "a".repeat(64), "patch", Map.of("url", "https://example.test"))).block(), "INVALID_ARGUMENT");
    }

    @Test
    void optionalConfigurationLoadsWithAndWithoutMcpApi() {
        var runner = new ApplicationContextRunner().withUserConfiguration(SoftwareMcpConfiguration.class)
            .withBean(SoftwareConsoleService.class, () -> service)
            .withBean(ReactiveSettingFetcher.class, () -> settings)
            .withBean(SoftwareCreationService.class, () -> creation)
            .withBean(SoftwarePreflightService.class, () -> preflight);
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBean("softwareMcpToolProvider")).isTrue();
        });
        runner.withClassLoader(new FilteredClassLoader("run.halo.mcpserver.api")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBean("softwareMcpToolProvider")).isFalse();
            assertThat(context.getBean(SoftwareConsoleService.class)).isSameAs(service);
        });
    }

    private McpToolDefinition tool(String name) {
        return provider.tools().filter(tool -> tool.name().equals(name)).single().block();
    }

    private static Mono<McpToolResult> invoke(McpToolDefinition tool, Map<String, Object> arguments) {
        return tool.handler().execute(new McpToolInvocation(tool.name(), arguments))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new TestingAuthenticationToken("admin", "unused", "ROLE_super-role")));
    }

    private static void assertError(McpToolResult result, String code) {
        assertThat(result.error()).isTrue();
        assertThat(SoftwareMcpSchemas.cast(result.structuredContent().get("error"))).containsEntry("code", code);
    }

    private static Map<String, Object> validArguments(String name) {
        if (name.equals("get_options") || name.equals("get_stats") || name.startsWith("list_")) { return Map.of(); }
        if (name.equals("get_create_operation")) { return Map.of("resourceType", "app", "requestId", "00000000-0000-0000-0000-000000000001"); }
        if (name.startsWith("check_")) { return Map.of("name", "resource"); }
        if (name.startsWith("get_")) { return Map.of("name", "resource"); }
        if (name.startsWith("delete_")) { return Map.of("name", "resource", "expectedVersion", 7); }
        if (name.startsWith("set_")) { return Map.of("name", "resource", "expectedVersion", 7, "published", true); }
        if (name.equals("update_download_asset_secrets")) {
            return Map.of("name", "resource", "expectedVersion", 7, "patch", Map.of("extractionCode", "new-code"));
        }
        for (var kind : SoftwareResourceKind.values()) {
            if (name.equals("create_" + kind.singular())) { return Map.of("spec", requiredSpec(kind)); }
            if (name.equals("update_" + kind.singular())) {
                String field = switch (kind) {
                    case APP -> "summary";
                    case VERSION -> "build";
                    case DOWNLOAD_ASSET -> "title";
                    default -> "description";
                };
                return Map.of("name", "resource", "expectedVersion", 7, "patch", Map.of(field, "Changed"));
            }
        }
        throw new AssertionError("Missing sample for " + name);
    }

    private static Map<String, Object> requiredSpec(SoftwareResourceKind kind) {
        var spec = new LinkedHashMap<String, Object>();
        kind.required().forEach(key -> spec.put(key, key.equals("slug") ? "example" : "Example"));
        return spec;
    }

    private static AbstractExtension fixture(SoftwareResourceKind kind) {
        var resource = org.springframework.beans.BeanUtils.instantiateClass(kind.type());
        var metadata = new Metadata(); metadata.setName("resource"); metadata.setVersion(7L);
        metadata.setAnnotations(Map.of("private", "annotation-secret"));
        resource.setMetadata(metadata);
        kind.setSpec(resource, SoftwareMutation.createSpec(kind, requiredSpec(kind)));
        if (resource instanceof DownloadAsset asset) {
            asset.getSpec().setUrl("https://example.com/private.zip");
            asset.getSpec().setStorageKey("storage-secret");
            asset.getSpec().setExtractionCode("extraction-secret");
            asset.getSpec().setPasswordSecretName("password-secret");
        }
        return resource;
    }
}
