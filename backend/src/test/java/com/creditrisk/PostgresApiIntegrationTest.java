package com.creditrisk;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the same API tests against a real, throwaway PostgreSQL started in Docker.
 * This catches anything H2 tolerates but PostgreSQL doesn't (SQL dialect, types, Flyway scripts).
 *
 * <p>Skipped automatically when Docker isn't available (e.g. a laptop without Docker);
 * always runs in GitHub Actions, where Docker is pre-installed.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresApiIntegrationTest extends AbstractApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
