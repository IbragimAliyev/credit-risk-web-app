package com.creditrisk.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void convertsRenderStyleUrl() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.convert(
                "postgresql://risk_user:s3cr%40t@dpg-abc123-a.oregon-postgres.render.com:5432/creditrisk");
        assertEquals("jdbc:postgresql://dpg-abc123-a.oregon-postgres.render.com:5432/creditrisk",
                p.get("spring.datasource.url"));
        assertEquals("risk_user", p.get("spring.datasource.username"));
        assertEquals("s3cr@t", p.get("spring.datasource.password"));
    }

    @Test
    void defaultsPortAndKeepsQuery() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.convert(
                "postgres://u:p@internal-host/db?sslmode=require");
        assertEquals("jdbc:postgresql://internal-host:5432/db?sslmode=require", p.get("spring.datasource.url"));
    }

    @Test
    void appliedOnlyWhenDatabaseUrlSetAndDbUrlAbsent() {
        MockEnvironment env = new MockEnvironment().withProperty("DATABASE_URL", "postgresql://u:p@h:5433/d");
        processor.postProcessEnvironment(env, null);
        assertEquals("jdbc:postgresql://h:5433/d", env.getProperty("spring.datasource.url"));

        MockEnvironment explicit = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://u:p@h:5433/d")
                .withProperty("DB_URL", "jdbc:postgresql://other:5432/x");
        processor.postProcessEnvironment(explicit, null);
        assertFalse(explicit.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.SOURCE_NAME));

        MockEnvironment none = new MockEnvironment();
        processor.postProcessEnvironment(none, null);
        assertNull(none.getProperty("spring.datasource.url"));
    }

    @Test
    void rejectsNonPostgresScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseUrlEnvironmentPostProcessor.convert("mysql://u:p@h/db"));
    }
}
