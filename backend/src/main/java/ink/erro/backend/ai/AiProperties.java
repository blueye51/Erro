package ink.erro.backend.ai;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ai")
public record AiProperties(@NotNull URI endpoint, String apiKey, String model,
                           @Min(1) @Max(32768) int maxOutputTokens, @NotNull Duration timeout) {
}
