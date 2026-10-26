package ink.erro.backend.document;

import java.time.Duration;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("documents")
public record DocumentProperties(boolean enabled, @NotBlank String visionModel,
        @NotNull Duration ttl, @Min(1) @Max(64) int maxProblems) {}
