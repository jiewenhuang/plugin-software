package run.jiewen.software.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.ReactiveExtensionClient;
import run.jiewen.software.SoftwareDomainException;
import run.jiewen.software.api.SoftwareResourceKind;
import run.jiewen.software.extension.SoftwareCreateOperation;
import run.jiewen.software.finders.SoftwareConsoleService;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.annotation.JsonDeserialize;

class SoftwareCreationServiceTest {
    private static final String ID = "a0000000-0000-0000-0000-000000000001";
    private static final Map<String, Object> INPUT = Map.of("slug", "example", "displayName", "Example");
    @JsonDeserialize(as = Metadata.class)
    private interface ConcreteMetadata {}
    private static final JsonMapper JSON = JsonMapper.builder().addMixIn(MetadataOperator.class, ConcreteMetadata.class).build();
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final SoftwareConsoleService domain = mock(SoftwareConsoleService.class);
    private final SoftwareCreationService service = new SoftwareCreationService(client, domain);
    private final Map<String, AbstractExtension> store = new ConcurrentHashMap<>();
    private final AtomicInteger resourceCreates = new AtomicInteger();

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void storage() {
        doAnswer(call -> Mono.defer(() -> Mono.justOrEmpty(store.get(key(call.getArgument(0), call.getArgument(1))))
            .map(SoftwareCreationServiceTest::copy)))
            .when(client).fetch(any(Class.class), anyString());
        doAnswer(call -> Mono.defer(() -> {
            AbstractExtension resource = call.getArgument(0);
            if (!(resource instanceof SoftwareCreateOperation)) { resourceCreates.incrementAndGet(); }
            var value = copy(resource); value.getMetadata().setVersion(0L);
            value.getMetadata().setCreationTimestamp(Instant.now());
            return store.putIfAbsent(key(value.getClass(), value.getMetadata().getName()), value) == null
                ? Mono.just(copy(value)) : Mono.error(new DuplicateKeyException("exists"));
        })).when(client).create(any(AbstractExtension.class));
        doAnswer(call -> Mono.defer(() -> {
            AbstractExtension resource = call.getArgument(0);
            String key = key(resource.getClass(), resource.getMetadata().getName());
            synchronized (store) {
                var current = store.get(key);
                if (current == null || !current.getMetadata().getVersion().equals(resource.getMetadata().getVersion())) {
                    return Mono.error(new OptimisticLockingFailureException("changed"));
                }
                var value = copy(resource); value.getMetadata().setVersion(current.getMetadata().getVersion() + 1);
                store.put(key, value); return Mono.just(copy(value));
            }
        })).when(client).update(any(AbstractExtension.class));
        when(domain.prepareCreateResource(any(), anyMap(), anyString())).thenAnswer(call -> {
            SoftwareResourceKind kind = call.getArgument(0);
            var resource = org.springframework.beans.BeanUtils.instantiateClass(kind.type());
            var metadata = new Metadata(); metadata.setName(call.getArgument(2)); resource.setMetadata(metadata);
            kind.setSpec(resource, run.jiewen.software.api.SoftwareMutation.createSpec(kind, call.getArgument(1)));
            return Mono.just(resource);
        });
    }

    @Test
    void duplicateRequestSurvivesServiceRestartAndResourceDeletion() {
        var first = service.create(SoftwareResourceKind.APP, INPUT, ID).block();
        assertThat(first.receipt().replayed()).isFalse();
        var restarted = new SoftwareCreationService(client, domain);
        var replay = restarted.create(SoftwareResourceKind.APP, Map.of("displayName", "Example", "slug", "example"), ID.toUpperCase()).block();
        assertThat(replay.receipt().name()).isEqualTo(first.receipt().name());
        assertThat(replay.receipt().replayed()).isTrue();
        assertThat(replay.resource()).isNull();
        store.remove(key(SoftwareResourceKind.APP.type(), first.receipt().name()));
        assertThat(restarted.create(SoftwareResourceKind.APP, INPUT, ID).block().receipt().state()).isEqualTo("succeeded");
        assertThat(resourceCreates).hasValue(1);
        assertThat(JSON.writeValueAsString(operation())).doesNotContain("displayName", "Example");
    }

    @Test
    void sameIdWithDifferentInputIsRejectedWithoutAnotherWrite() {
        service.create(SoftwareResourceKind.APP, INPUT, ID).block();
        error(service.create(SoftwareResourceKind.APP, Map.of("slug", "changed", "displayName", "Changed"), ID), "IDEMPOTENCY_CONFLICT");
        assertThat(resourceCreates).hasValue(1);
    }

    @Test
    void validationFailureIsDurableAndNoResourceWasCreated() {
        doReturn(Mono.error(new SoftwareDomainException("REFERENCE_INVALID", "invalid reference")))
            .when(domain).prepareCreateResource(any(), anyMap(), anyString());
        error(service.create(SoftwareResourceKind.APP, INPUT, ID), "REFERENCE_INVALID");
        error(service.create(SoftwareResourceKind.APP, INPUT, ID), "REFERENCE_INVALID");
        var status = service.status(SoftwareResourceKind.APP, ID).block();
        assertThat(status.state()).isEqualTo("failed");
        assertThat(status.errorCode()).isEqualTo("REFERENCE_INVALID");
        assertThat(resourceCreates).hasValue(0);
    }

    @Test
    void concurrentRequestsClaimOnceAndCancellationRemainsAmbiguous() {
        Sinks.One<AbstractExtension> inFlight = Sinks.one();
        doAnswer(call -> { resourceCreates.incrementAndGet(); return inFlight.asMono(); })
            .when(client).create(any(run.jiewen.software.extension.SoftwareApp.class));
        StepVerifier.create(service.create(SoftwareResourceKind.APP, INPUT, ID))
            .then(() -> error(service.create(SoftwareResourceKind.APP, INPUT, ID), "OPERATION_IN_PROGRESS"))
            .thenCancel().verify();
        var pending = operation(); pending.getSpec().setStartedAt(Instant.now().minusSeconds(60));
        store.put(key(SoftwareCreateOperation.class, pending.getMetadata().getName()), pending);
        error(service.create(SoftwareResourceKind.APP, INPUT, ID), "WRITE_OUTCOME_UNKNOWN");
        assertThat(service.status(SoftwareResourceKind.APP, ID).block().state()).isEqualTo("unknown");
        assertThat(resourceCreates).hasValue(1);
    }

    @Test
    void crashAfterCommitRecoversFromAtomicResourceMarker() {
        var result = service.create(SoftwareResourceKind.APP, INPUT, ID).block();
        var operation = operation(); operation.getSpec().setState("writing"); operation.getSpec().setResourceVersion(null);
        store.put(key(SoftwareCreateOperation.class, operation.getMetadata().getName()), operation);
        var restarted = new SoftwareCreationService(client, domain);
        assertThat(restarted.status(SoftwareResourceKind.APP, ID).block().state()).isEqualTo("succeeded");
        assertThat(restarted.create(SoftwareResourceKind.APP, INPUT, ID).block().receipt().name()).isEqualTo(result.receipt().name());
        assertThat(resourceCreates).hasValue(1);
    }

    @Test
    void pendingReservationCanResumeButWritingWithMissingTargetCannot() {
        service.create(SoftwareResourceKind.APP, INPUT, ID).block();
        var op = operation(); store.remove(key(SoftwareResourceKind.APP.type(), op.getSpec().getTargetName()));
        op.getSpec().setState("pending"); op.getSpec().setResourceVersion(null);
        store.put(key(SoftwareCreateOperation.class, op.getMetadata().getName()), op);
        assertThat(service.create(SoftwareResourceKind.APP, INPUT, ID).block().receipt().state()).isEqualTo("succeeded");
        assertThat(resourceCreates).hasValue(2);
        op = operation(); store.remove(key(SoftwareResourceKind.APP.type(), op.getSpec().getTargetName()));
        op.getSpec().setState("writing"); op.getSpec().setStartedAt(Instant.now().minusSeconds(60));
        store.put(key(SoftwareCreateOperation.class, op.getMetadata().getName()), op);
        error(service.create(SoftwareResourceKind.APP, INPUT, ID), "WRITE_OUTCOME_UNKNOWN");
        assertThat(resourceCreates).hasValue(2);
    }

    @Test
    void receiptPersistenceFailureDoesNotHideAnAmbiguousCommittedWrite() {
        var failReceipt = new java.util.concurrent.atomic.AtomicBoolean(true);
        // Wrap the existing CAS store answer, failing only after the resource has committed.
        doAnswer(call -> Mono.defer(() -> {
            SoftwareCreateOperation value = call.getArgument(0);
            if ("succeeded".equals(value.getSpec().getState()) && failReceipt.get()) {
                return Mono.error(new IllegalStateException("database unavailable"));
            }
            String key = key(SoftwareCreateOperation.class, value.getMetadata().getName());
            var current = store.get(key);
            if (!current.getMetadata().getVersion().equals(value.getMetadata().getVersion())) {
                return Mono.error(new OptimisticLockingFailureException("changed"));
            }
            var updated = copy(value);updated.getMetadata().setVersion(current.getMetadata().getVersion() + 1);
            store.put(key, updated);return Mono.just(copy(updated));
        })).when(client).update(any(SoftwareCreateOperation.class));
        error(service.create(SoftwareResourceKind.APP, INPUT, ID), "WRITE_OUTCOME_UNKNOWN");
        assertThat(resourceCreates).hasValue(1);
        failReceipt.set(false);
        assertThat(new SoftwareCreationService(client, domain).create(SoftwareResourceKind.APP, INPUT, ID)
            .block().receipt().state()).isEqualTo("succeeded");
        assertThat(resourceCreates).hasValue(1);
    }

    @Test
    void concurrentReservationHandlesHaloDuplicateNameValidation() {
        service.create(SoftwareResourceKind.APP, INPUT, ID).block();
        var existing = operation();
        // A stale initial read can miss the record created by another request.
        doReturn(Mono.empty(), Mono.just(copy(existing)))
            .when(client).fetch(eq(SoftwareCreateOperation.class), anyString());
        doReturn(Mono.error(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Duplicate name detected.")))
            .when(client).create(any(SoftwareCreateOperation.class));
        assertThat(service.create(SoftwareResourceKind.APP, INPUT, ID).block().receipt().replayed()).isTrue();
        assertThat(resourceCreates).hasValue(1);
    }

    private SoftwareCreateOperation operation() {
        return (SoftwareCreateOperation) copy(store.values().stream().filter(SoftwareCreateOperation.class::isInstance).findFirst().orElseThrow());
    }

    private static String key(Class<?> type, String name) { return type.getName() + "/" + name; }
    private static AbstractExtension copy(AbstractExtension value) { return JSON.convertValue(value, value.getClass()); }
    private static void error(Mono<?> mono, String code) {
        StepVerifier.create(mono).expectErrorSatisfies(error ->
            assertThat(((SoftwareDomainException) error).code()).isEqualTo(code)).verify();
    }
}
