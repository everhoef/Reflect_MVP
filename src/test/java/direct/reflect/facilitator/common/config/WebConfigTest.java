package direct.reflect.facilitator.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebConfigTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WebConfig()).build();
    }

    @Test
    void forwardsLoginRouteToSpaIndex() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsHomeRouteToSpaIndex() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsRetroDeepLinkToSpaIndex() throws Exception {
        mockMvc.perform(get("/retro/019cdc01-9a8f-7f92-b265-42948855ac9b"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void doesNotForwardNonGetRequests() throws Exception {
        mockMvc.perform(post("/login"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void doesNotClaimApiRoutes() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isNotFound());
    }
}
