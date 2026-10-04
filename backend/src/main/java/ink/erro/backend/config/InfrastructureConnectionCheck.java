package ink.erro.backend.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.S3Client;

@Component
@ConditionalOnProperty(name = "infrastructure.verify-on-startup", havingValue = "true", matchIfMissing = true)
public class InfrastructureConnectionCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureConnectionCheck.class);

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ObjectProvider<S3Client> s3Client;

    public InfrastructureConnectionCheck(DataSource dataSource,
                                         RedisConnectionFactory redisConnectionFactory,
                                         ObjectProvider<S3Client> s3Client) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.s3Client = s3Client;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) {
                throw new IllegalStateException("PostgreSQL connection check failed");
            }
        }
        log.info("PostgreSQL connection verified");

        try (var connection = redisConnectionFactory.getConnection()) {
            if (!"PONG".equals(connection.ping())) {
                throw new IllegalStateException("Redis connection check failed");
            }
        }
        log.info("Redis connection verified");

        var storage = s3Client.getIfAvailable();
        if (storage != null) {
            // Read-only: verifies credentials without creating a bucket or uploading data.
            storage.listBuckets();
            log.info("S3 storage connection verified");
        } else {
            log.info("S3 storage disabled; skipping its connection check");
        }
        log.info("Infrastructure ready");
    }
}
