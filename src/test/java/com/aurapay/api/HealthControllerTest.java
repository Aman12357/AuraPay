package com.aurapay.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsRealPaymentsDisabled() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("AuraPay"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.realPaymentsEnabled").value(false));
    }

    @Test
    void creditPackCatalogIsAvailable() throws Exception {
        mockMvc.perform(get("/api/credit-packs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("starter-100"))
                .andExpect(jsonPath("$[0].credits").value(100));
    }
}
