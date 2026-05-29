package direct.reflect.facilitator.facilitation;

import com.redis.testcontainers.RedisContainer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import direct.reflect.facilitator.config.TestSecurityOverride;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization matrix integration test.
 *
 * <p>Verifies that unauthenticated requests to {@code /api/**} endpoints return HTTP 401
 * with a JSON error body, and do NOT redirect to {@code /login} (302).
 *
 * <p>Root cause: Previously, {@code anyRequest().permitAll()} in Spring Security meant that
 * method-level {@code @PreAuthorize} rejections fell through to the default
 * {@code ExceptionTranslationFilter}, which redirected unauthenticated users to {@code /login}.
 * The fix ({@code anyRequest().authenticated()}) ensures the HTTP-layer rule fires first,
 * invoking the custom {@code authenticationEntryPoint} that returns 401 JSON for API requests
 * and redirects to {@code /login} only for browser (non-API) requests.
 *
 * <p>Layer: Spring/API integration (MockMvc, Testcontainers, no browser).
 * <p>Naming convention: {@code ApiIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"import", "test"})
@Import({TestSecurityOverride.class, direct.reflect.facilitator.config.TestRedisConfig.class})
@DisplayName("Authorization Matrix — unauthenticated /api/** requests return 401")
@Slf4j
class AuthorizationMatrixIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static RedisContainer redisContainer = new RedisContainer("redis:alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    // ==================== POST /api/retros ====================

    @Test
    @DisplayName("POST /api/retros — unauthenticated → 401 JSON, not 302 redirect")
    void createRetro_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/retros")
                        .contentType("application/json")
                        .content("{\"name\":\"test\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ==================== POST /api/retros/{id}/advance ====================

    @Test
    @DisplayName("POST /api/retros/{id}/advance — unauthenticated → 401 JSON, not 302 redirect")
    void advanceStep_Unauthenticated_Returns401() throws Exception {
        UUID fakeRetroId = UUID.randomUUID();
        mockMvc.perform(post("/api/retros/{retroId}/advance", fakeRetroId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ==================== POST /api/retros/{id}/start ====================

    @Test
    @DisplayName("POST /api/retros/{id}/start — unauthenticated → 401 JSON, not 302 redirect")
    void startSession_Unauthenticated_Returns401() throws Exception {
        UUID fakeRetroId = UUID.randomUUID();
        mockMvc.perform(post("/api/retros/{retroId}/start", fakeRetroId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET /api/retros/{id}/events ====================

    @Test
    @DisplayName("GET /api/retros/{id}/events — unauthenticated → 401 JSON, not 302 redirect")
    void sseStream_Unauthenticated_Returns401() throws Exception {
        UUID fakeRetroId = UUID.randomUUID();
        mockMvc.perform(get("/api/retros/{retroId}/events", fakeRetroId))
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET /api/me/retros/active ====================

    @Test
    @DisplayName("GET /api/me/retros/active — unauthenticated → 401 JSON, not 302 redirect")
    void activeSessions_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/me/retros/active"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/retro/create — legacy route → 404")
    void legacyCreateRetro_Returns404() throws Exception {
        mockMvc.perform(post("/api/retro/create")
                        .contentType("application/json")
                        .content("{\"name\":\"test\"}")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/retro/{id}/next — legacy route → 404")
    void legacyAdvanceStep_Returns404() throws Exception {
        UUID fakeRetroId = UUID.randomUUID();
        mockMvc.perform(post("/api/retro/{retroId}/next", fakeRetroId)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/retro/join — legacy route → 404")
    void legacyJoinRetro_Returns404() throws Exception {
        mockMvc.perform(post("/api/retro/join")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ==================== Response body contract ====================

    @Test
    @DisplayName("401 response body contains JSON error payload with loginUrl")
    void unauthenticated_Returns401_WithJsonBody() throws Exception {
        UUID fakeRetroId = UUID.randomUUID();
        mockMvc.perform(post("/api/retros/{retroId}/advance", fakeRetroId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json("{\"error\":\"Authentication required\",\"loginUrl\":\"/login\"}"));
    }

}
