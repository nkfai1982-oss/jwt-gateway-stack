package com.example.jwtgateway.springapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WhoAmIControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsRequestsWithoutGatewayIdentity() throws Exception {
        mockMvc.perform(get("/whoami"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("gateway_identity_required"));
    }

    @Test
    void rejectsTheWrongAudience() throws Exception {
        mockMvc.perform(get("/whoami")
                        .header("X-Authenticated-User", "alice")
                        .header("X-Authenticated-Audience", "component-b")
                        .header("X-Authenticated-Scopes", "component-a.read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsOnlyRequestsWithComponentAScope() throws Exception {
        mockMvc.perform(get("/whoami")
                        .header("X-Authenticated-User", "alice")
                        .header("X-Authenticated-Audience", "component-a")
                        .header("X-Authenticated-Scopes", "component-a.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("component-a"))
                .andExpect(jsonPath("$.user").value("alice"));
    }
}
