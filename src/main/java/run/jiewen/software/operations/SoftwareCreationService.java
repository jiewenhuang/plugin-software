package run.jiewen.software.operations;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.jiewen.software.SoftwareDomainException;
import run.jiewen.software.api.SoftwareResourceKind;
import run.jiewen.software.extension.SoftwareCreateOperation;
import run.jiewen.software.finders.SoftwareConsoleService;
import tools.jackson.databind.json.JsonMapper;

/** At-most-one creation attempt per request. Ambiguous missing targets are never recreated. */
@Component
public class SoftwareCreationService {
    public static final String REQUEST_MARKER = "software.jiewen.run/create-operation";
    private static final Duration IN_PROGRESS_WINDOW = Duration.ofSeconds(30);
    private final ReactiveExtensionClient client;
    private final SoftwareConsoleService service;

    public record Outcome(AbstractExtension resource, Receipt receipt) {}
    public record Receipt(String requestId, String resourceType, String name, String state,
        Long resourceVersion, String errorCode, boolean replayed) {}

    public SoftwareCreationService(ReactiveExtensionClient client, SoftwareConsoleService service) {
        this.client = client;
        this.service = service;
    }

    public Mono<Outcome> create(SoftwareResourceKind kind, Map<String, Object> spec, String requestId) {
        return Mono.defer(() -> {
            String id = canonicalId(requestId);
            String digest = SoftwareDigests.sha256(spec);
            return reserve(kind, id, digest).flatMap(operation -> {
                if (!digest.equals(operation.getSpec().getRequestDigest())) {
                    return Mono.error(new SoftwareDomainException("IDEMPOTENCY_CONFLICT",
                        "requestId was already used with different input; use the original request or a new ID"));
                }
                return "pending".equals(operation.getSpec().getState())
                    ? prepare(kind, spec, id, operation) : replay(kind, id, operation);
            });
        });
    }

    public Mono<Receipt> status(SoftwareResourceKind kind, String requestId) {
        return Mono.defer(() -> {
            String id = canonicalId(requestId);
            return fetch(operationName(kind, id))
                .flatMap(operation -> reconcile(kind, operation))
                .map(operation -> receipt(id, operation, true));
        });
    }

    private Mono<SoftwareCreateOperation> reserve(SoftwareResourceKind kind, String id, String digest) {
        String name = operationName(kind, id);
        return client.fetch(SoftwareCreateOperation.class, name).switchIfEmpty(Mono.defer(() -> {
            var operation = new SoftwareCreateOperation();
            var metadata = new Metadata(); metadata.setName(name); operation.setMetadata(metadata);
            var spec = new SoftwareCreateOperation.Spec();
            spec.setResourceType(kind.singular()); spec.setRequestDigest(digest);
            spec.setTargetName("software-" + kind.singular().replace('_', '-') + "-mcp-" + id.replace("-", ""));
            spec.setState("pending"); operation.setSpec(spec);
            return client.create(operation).onErrorResume(error -> {
                // Halo may reject a duplicate name before storage with a 400, rather than DuplicateKeyException.
                // Adopt only an actually persisted record; never infer success from exception text.
                if (!(error instanceof DuplicateKeyException
                    || error instanceof ResponseStatusException response && response.getStatusCode().value() == 400)) {
                    return Mono.error(error);
                }
                return client.fetch(SoftwareCreateOperation.class, name).switchIfEmpty(Mono.error(error));
            });
        }));
    }

    private Mono<Outcome> prepare(SoftwareResourceKind kind, Map<String, Object> input, String id,
        SoftwareCreateOperation operation) {
        // Validation is side-effect free. Only the winner of the pending -> writing CAS may create.
        return service.prepareCreateResource(kind, input, operation.getSpec().getTargetName())
            .onErrorResume(error -> {
                if (!(error instanceof ServerWebInputException)) { return Mono.error(error); }
                var failed = copy(operation);
                failed.getSpec().setState("failed");
                failed.getSpec().setErrorCode(error instanceof SoftwareDomainException domain
                    ? domain.code() : "INVALID_ARGUMENT");
                return client.update(failed).then(Mono.<AbstractExtension>error(error));
            })
            .flatMap(resource -> {
                var writing = copy(operation);
                writing.getSpec().setState("writing"); writing.getSpec().setStartedAt(Instant.now());
                return client.update(writing).flatMap(claim -> {
                    resource.getMetadata().setAnnotations(Map.of(REQUEST_MARKER, marker(claim)));
                    return client.create(resource)
                        .flatMap(created -> finish(claim, created).map(done -> new Outcome(created, receipt(id, done, false))))
                        .onErrorResume(error -> replay(kind, id, claim)
                            .onErrorMap(recoveryError -> recoveryError instanceof SoftwareDomainException
                                ? recoveryError : new SoftwareDomainException("WRITE_OUTCOME_UNKNOWN",
                                    "Creation may have committed; retry the identical requestId to recover its receipt")));
                });
            })
            .onErrorResume(OptimisticLockingFailureException.class,
                error -> fetch(operation.getMetadata().getName()).flatMap(current -> replay(kind, id, current)));
    }

    private Mono<Outcome> replay(SoftwareResourceKind kind, String id, SoftwareCreateOperation operation) {
        return reconcile(kind, operation).flatMap(current -> {
            String state = current.getSpec().getState();
            if ("succeeded".equals(state)) {
                return Mono.just(new Outcome(null, receipt(id, current, true)));
            }
            if ("failed".equals(state)) {
                return Mono.error(new SoftwareDomainException(current.getSpec().getErrorCode(),
                    "This request previously failed validation; fix the input/references and use a new requestId"));
            }
            String code = uncertain(current) ? "WRITE_OUTCOME_UNKNOWN" : "OPERATION_IN_PROGRESS";
            return Mono.error(new SoftwareDomainException(code,
                "Creation may be in progress or completed; query get_create_operation or retry the identical requestId. "
                    + "An ambiguous request will not create another resource"));
        });
    }

    private Mono<SoftwareCreateOperation> reconcile(SoftwareResourceKind kind, SoftwareCreateOperation operation) {
        if (!"writing".equals(operation.getSpec().getState())) { return Mono.just(operation); }
        return client.fetch(kind.type(), operation.getSpec().getTargetName())
            .filter(resource -> resource.getMetadata().getAnnotations() != null
                && marker(operation).equals(resource.getMetadata().getAnnotations().get(REQUEST_MARKER)))
            .flatMap(resource -> finish(operation, resource)).defaultIfEmpty(operation);
    }

    private Mono<SoftwareCreateOperation> finish(SoftwareCreateOperation operation, AbstractExtension resource) {
        var done = copy(operation);
        done.getSpec().setState("succeeded");
        done.getSpec().setResourceVersion(resource.getMetadata().getVersion());
        return client.update(done).onErrorResume(OptimisticLockingFailureException.class,
            error -> fetch(operation.getMetadata().getName()).flatMap(current ->
                "succeeded".equals(current.getSpec().getState()) ? Mono.just(current) : Mono.error(error)));
    }

    private Mono<SoftwareCreateOperation> fetch(String name) {
        return client.fetch(SoftwareCreateOperation.class, name)
            .filter(operation -> operation.getMetadata().getDeletionTimestamp() == null)
            .switchIfEmpty(Mono.error(new SoftwareDomainException("NOT_FOUND", "Creation request not found")));
    }

    private static Receipt receipt(String id, SoftwareCreateOperation operation, boolean replayed) {
        var spec = operation.getSpec();
        return new Receipt(id, spec.getResourceType(), spec.getTargetName(),
            uncertain(operation) ? "unknown" : spec.getState(), spec.getResourceVersion(), spec.getErrorCode(), replayed);
    }

    private static boolean uncertain(SoftwareCreateOperation operation) {
        return "writing".equals(operation.getSpec().getState())
            && (operation.getSpec().getStartedAt() == null
                || operation.getSpec().getStartedAt().plus(IN_PROGRESS_WINDOW).isBefore(Instant.now()));
    }

    private static String marker(SoftwareCreateOperation operation) {
        return operation.getMetadata().getName() + ":" + operation.getSpec().getRequestDigest();
    }

    private static String operationName(SoftwareResourceKind kind, String id) {
        return kind.singular().replace('_', '-') + "-" + id;
    }

    private static String canonicalId(String id) {
        if (id == null || !id.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) {
            throw new SoftwareDomainException("INVALID_ARGUMENT", "requestId must be a UUID");
        }
        return UUID.fromString(id).toString();
    }

    private static SoftwareCreateOperation copy(SoftwareCreateOperation operation) {
        var copy = new SoftwareCreateOperation();
        copy.setMetadata(JsonMapper.shared().convertValue(operation.getMetadata(), Metadata.class));
        copy.setSpec(JsonMapper.shared().convertValue(operation.getSpec(), SoftwareCreateOperation.Spec.class));
        return copy;
    }
}
