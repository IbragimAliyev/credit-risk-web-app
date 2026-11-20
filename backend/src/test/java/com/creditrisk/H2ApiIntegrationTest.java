package com.creditrisk;

import org.springframework.test.context.TestPropertySource;

/** Runs the API tests on in-memory H2 (PostgreSQL mode): fast, and needs no Docker. */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class H2ApiIntegrationTest extends AbstractApiIntegrationTest {
}
