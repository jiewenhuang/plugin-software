package run.jiewen.software.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.jiewen.software.finders.SoftwareConsoleService;
import run.jiewen.software.operations.SoftwareCreationService;
import run.jiewen.software.operations.SoftwarePreflightService;

@Configuration
@ConditionalOnClass(name = "run.halo.mcpserver.api.McpToolProvider")
public class SoftwareMcpConfiguration {
    @Bean
    SoftwareMcpToolProvider softwareMcpToolProvider(SoftwareConsoleService service,
        ReactiveSettingFetcher settings, SoftwareCreationService creation, SoftwarePreflightService preflight) {
        return new SoftwareMcpToolProvider(service, settings, creation, preflight);
    }
}
