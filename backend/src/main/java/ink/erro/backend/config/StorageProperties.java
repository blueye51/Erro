package ink.erro.backend.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storage.s3")
public record StorageProperties(URI endpoint, String region, boolean pathStyleAccess) {
}
