package com.aurapay.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndRetrievesDemoOrderWithoutEnablingPayments() throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packId\":\"starter-100\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.packId").value("starter-100"))
                .andExpect(jsonPath("$.credits").value(100))
                .andExpect(jsonPath("$.amountInPaise").value(10000))
                .andExpect(jsonPath("$.status").value("DEMO_CREATED"))
                .andExpect(jsonPath("$.realPaymentsEnabled").value(false))
                .andReturn().getResponse().getContentAsString();

        String orderId = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build().readTree(response).get("orderId").asText();

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("DEMO_CREATED"));
    }

    @Test
    void rejectsUnknownCreditPack() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packId\":\"made-up-pack\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown credit pack"));
    }
}
