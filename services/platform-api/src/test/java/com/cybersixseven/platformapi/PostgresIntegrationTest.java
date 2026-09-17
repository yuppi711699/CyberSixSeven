package com.cybersixseven.platformapi;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * One Postgres for the whole JVM test run.
 *
 * <p>Do not use {@code @Container}: JUnit stops the container when the first test class finishes,
 * while Spring keeps a cached context whose Hikari pool still points at the dead port. The next
 * class then fails with "connection has been closed" / missing tables.
 */
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:3000")
abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @MockitoBean
    SnsClient snsClient;

    @org.junit.jupiter.api.BeforeEach
    void stubSnsPublish() {
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("test-sns").build());
    }
}
