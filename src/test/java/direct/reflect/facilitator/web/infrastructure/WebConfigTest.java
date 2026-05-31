package direct.reflect.facilitator.web.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebConfigTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.routerFunctions(new WebConfig().spaFallback()).build();
    }

    @Nested
    class ServedBySpaCatchAll {

        @Test
        void servesIndexHtmlForLoginRoute() throws Exception {
            mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void servesIndexHtmlForUnknownFrontendRoute() throws Exception {
            mockMvc.perform(get("/future-route").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void servesIndexHtmlForHomeRoute() throws Exception {
            mockMvc.perform(get("/home").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void servesIndexHtmlForRetroDeepLink() throws Exception {
            mockMvc.perform(get("/retro/019cdc01-9a8f-7f92-b265-42948855ac9b").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void servesIndexHtmlForProfileRoute() throws Exception {
            mockMvc.perform(get("/profile/settings").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void servesIndexHtmlForAdminRoute() throws Exception {
            mockMvc.perform(get("/admin/dashboard").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(forwardedUrl(null));
        }
    }

    @Nested
    class NotCapturedBySpaFallback {

        @Test
        void doesNotServeIndexHtmlForPostRequests() throws Exception {
            mockMvc.perform(post("/login").accept(MediaType.TEXT_HTML))
                    .andExpect(status().is4xxClientError())
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void doesNotServeIndexHtmlForNonHtmlAccept() throws Exception {
            mockMvc.perform(get("/future-route").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void doesNotServeIndexHtmlForStaticAssets() throws Exception {
            mockMvc.perform(get("/assets/index-Cg_3nkxm.js").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound())
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void doesNotServeIndexHtmlForApiPathWithHtmlAccept() throws Exception {
            mockMvc.perform(get("/api/retros/anything").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound())
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void doesNotServeIndexHtmlForAuthPathWithHtmlAccept() throws Exception {
            mockMvc.perform(get("/auth/guest").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound())
                    .andExpect(forwardedUrl(null));
        }

        @Test
        void doesNotServeIndexHtmlForActuatorPathWithHtmlAccept() throws Exception {
            mockMvc.perform(get("/actuator/health").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound())
                    .andExpect(forwardedUrl(null));
        }
    }
}
