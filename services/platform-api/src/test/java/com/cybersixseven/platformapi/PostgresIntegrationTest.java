package com.cybersixseven.platformapi;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres for the whole JVM test run.
 *
 * <p>Do not use {@code @Container}: JUnit stops the container when the first test class finishes,
 * while Spring keeps a cached context whose Hikari pool still points at the dead port. The next
 * class then fails with "connection has been closed" / missing tables.
 */
abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }
}
