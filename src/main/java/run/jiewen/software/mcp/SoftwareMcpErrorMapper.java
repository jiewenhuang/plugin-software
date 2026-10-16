package run.jiewen.software.mcp;

import java.util.UUID;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import run.halo.mcpserver.api.McpToolResult;
import run.jiewen.software.SoftwareDomainException;
import tools.jackson.core.JacksonException;

@Slf4j
final class SoftwareMcpErrorMapper {
    private SoftwareMcpErrorMapper() {}

    static McpToolResult result(Throwable error, boolean write) {
        if (error instanceof SoftwareDomainException domain) {
            return McpToolResult.error(domain.code(), domain.getReason());
        }
        if (error instanceof TimeoutException) {
            return McpToolResult.error(write ? "WRITE_OUTCOME_UNKNOWN" : "TIMEOUT",
                write ? "Operation timed out; inspect the resource before retrying the write"
                    : "Query timed out; narrow the query and retry");
        }
        if (error instanceof OptimisticLockingFailureException) {
            return McpToolResult.error("CONFLICT", "Resource changed; read it again before retrying");
        }
        if (error instanceof DuplicateKeyException) {
            return McpToolResult.error("ALREADY_EXISTS", "Resource already exists");
        }
        if (error instanceof AccessDeniedException) {
            return McpToolResult.error("FORBIDDEN", "The caller is not authorized");
        }
        if (error instanceof JacksonException) {
            return McpToolResult.error("INVALID_ARGUMENT", "Input does not match the resource field types");
        }
        if (error instanceof ResponseStatusException response) {
            return switch (response.getStatusCode().value()) {
                case 400 -> McpToolResult.error("INVALID_ARGUMENT", response.getReason() == null
                    ? "Invalid software input" : response.getReason());
                case 401, 403 -> McpToolResult.error("FORBIDDEN", "The caller is not authorized");
                case 404 -> McpToolResult.error("NOT_FOUND", "Software resource not found");
                case 409 -> McpToolResult.error("CONFLICT", "Resource changed; read it again");
                default -> internal(error);
            };
        }
        return internal(error);
    }

    private static McpToolResult internal(Throwable error) {
        var id = UUID.randomUUID().toString();
        // Messages and causes can contain URLs, credentials or serialized requests.
        log.error("Software MCP failure id={} type={} location={}", id,
            error.getClass().getName(), error.getStackTrace().length == 0 ? "unknown" : error.getStackTrace()[0]);
        return McpToolResult.error("INTERNAL", "Unexpected software error; diagnostic ID: " + id);
    }
}
