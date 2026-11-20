package com.creditrisk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests over real HTTP handling, security, validation, JPA and Flyway.
 * Subclasses choose the database: {@link H2ApiIntegrationTest} (fast, no Docker) and
 * {@link PostgresApiIntegrationTest} (real PostgreSQL via Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractApiIntegrationTest {

    static final String VALID_APPLICATION = """
            {"applicantName":"Bob Smith","age":34,"income":50000,"loanAmount":20000,
             "employmentYears":3,"creditHistoryYears":6,"previousDefault":false,
             "homeOwnership":"RENT","loanIntent":"DEBTCONSOLIDATION"}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void fullFlow() throws Exception {
        // Register
        String token = tokenFrom(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"alice@example.com","password":"password123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                .andReturn().getResponse().getContentAsString());

        // Duplicate register -> 409
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"ALICE@example.com","password":"password123"}"""))
                .andExpect(status().isConflict());

        // Login (wrong + right password)
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Unauthenticated access -> 401
        mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());

        // Validation error -> 400 with field errors
        mvc.perform(post("/api/applications").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicantName":"","age":10,"income":50000,"loanAmount":20000,
                                 "employmentYears":3,"creditHistoryYears":99,"homeOwnership":"RENT",
                                 "loanIntent":"PERSONAL"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.age").exists())
                .andExpect(jsonPath("$.errors.creditHistoryYears").exists())
                .andExpect(jsonPath("$.errors.previousDefault").exists());

        // Unknown enum value -> 400
        mvc.perform(post("/api/applications").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_APPLICATION.replace("\"RENT\"", "\"CASTLE\"")))
                .andExpect(status().isBadRequest());

        // Create + score
        String created = mvc.perform(post("/api/applications").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_APPLICATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riskScore").value(allOf(greaterThanOrEqualTo(0), lessThanOrEqualTo(100))))
                .andExpect(jsonPath("$.riskLabel").isNotEmpty())
                .andExpect(jsonPath("$.defaultProbability").isNumber())
                .andExpect(jsonPath("$.breakdown.length()").value(8))
                .andExpect(jsonPath("$.breakdown[0].effect").isNotEmpty())
                .andExpect(jsonPath("$.modelVersion").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode createdJson = json.readTree(created);
        long id = createdJson.get("id").asLong();
        int score = createdJson.get("riskScore").asInt();

        // List + get (read back from the database, breakdown included)
        mvc.perform(get("/api/applications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/applications/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantName").value("Bob Smith"))
                .andExpect(jsonPath("$.riskScore").value(score))
                .andExpect(jsonPath("$.homeOwnership").value("RENT"))
                .andExpect(jsonPath("$.breakdown.length()").value(8));

        // Another user cannot see Alice's application
        String otherToken = tokenFrom(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Eve","email":"eve@example.com","password":"password123"}"""))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/applications/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/applications").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.length()").value(0));

        // Model info
        mvc.perform(get("/api/model").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("logistic_regression"))
                .andExpect(jsonPath("$.metrics.testAUC").isNumber())
                .andExpect(jsonPath("$.thresholds.low").isNumber());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void corsPreflightAllowsFrontendOrigin() throws Exception {
        mvc.perform(options("/api/applications")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    private String tokenFrom(String body) throws Exception {
        return json.readTree(body).get("token").asText();
    }
}
