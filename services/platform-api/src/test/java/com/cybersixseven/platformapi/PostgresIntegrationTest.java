package com.cybersixseven.platformapi;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * One Postgres and one Redis for the whole JVM test run.
 *
 * <p>Do not use {@code @Container}: JUnit stops the container when the first test class finishes,
 * while Spring keeps a cached context whose pool still points at the dead port.
 */
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:3000,http://localhost:3001")
abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @MockitoBean
    SnsClient snsClient;

    @MockitoBean
    S3Presigner s3Presigner;

    @org.junit.jupiter.api.BeforeEach
    void stubSnsPublish() {
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("test-sns").build());
    }
}
