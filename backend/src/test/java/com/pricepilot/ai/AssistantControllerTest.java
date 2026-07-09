package com.pricepilot.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiClient aiClient;

    private Map<String, Object> mockResponse;

    @BeforeEach
    public void setUp() {
        mockResponse = new HashMap<>();
        mockResponse.put("response", "This is an answer.");
        mockResponse.put("conversationId", "conv-123");
    }

    @Test
    public void testChat_Unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"Hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testChat_Success() throws Exception {
        when(aiClient.chat(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/assistant/chat")
                .with(user("user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"gaming laptop under 80,000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("This is an answer."))
                .andExpect(jsonPath("$.conversationId").value("conv-123"));
    }

    @Test
    public void testCompare_Success() throws Exception {
        Map<String, Object> compResponse = new HashMap<>();
        compResponse.put("response", "Comparing Laptop A and B");
        when(aiClient.compare(any(), any())).thenReturn(compResponse);

        mockMvc.perform(post("/api/v1/assistant/compare")
                .with(user("user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\": [\"p1\", \"p2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Comparing Laptop A and B"));
    }

    @Test
    public void testAsk_Success() throws Exception {
        Map<String, Object> askResponse = new HashMap<>();
        askResponse.put("response", "Single turn answer");
        when(aiClient.ask(any(), any())).thenReturn(askResponse);

        mockMvc.perform(post("/api/v1/assistant/ask")
                .with(user("user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\": \"What is the best deal?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Single turn answer"));
    }

    @Test
    public void testClearMemory_Success() throws Exception {
        Map<String, Object> clearResponse = new HashMap<>();
        clearResponse.put("status", "success");
        when(aiClient.clearMemory(any(), any())).thenReturn(clearResponse);

        mockMvc.perform(post("/api/v1/assistant/clear_memory")
                .with(user("user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\": \"conv-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }
}
