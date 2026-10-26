package ink.erro.backend.guest;

import java.time.Duration;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("guest-chat")
public record GuestProperties(boolean cookieSecure, @NotNull Duration ttl) {
    public GuestProperties {
        if (ttl!=null && (ttl.compareTo(Duration.ofDays(1))<0 || ttl.compareTo(Duration.ofDays(730))>0))
            throw new IllegalArgumentException("Guest expiry must be between 1 and 730 days.");
    }
}
