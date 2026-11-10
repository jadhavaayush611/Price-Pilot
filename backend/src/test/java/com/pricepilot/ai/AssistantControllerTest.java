package com.pricepilot.ai;

import com.pricepilot.intelligence.assistant.model.AssistantConversationEntity;
import com.pricepilot.intelligence.assistant.repository.AssistantConversationRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssistantConversationRepository conversationRepository;

    @MockitoBean
    private AiClient aiClient;

    private UserEntity testUser;
    private Map<String, Object> mockResponse;

    @BeforeEach
    public void setUp() {
        testUser = userRepository.findByEmail("user@example.com").orElseGet(() ->
                userRepository.save(UserEntity.builder()
                        .email("user@example.com")
                        .firstName("Test")
                        .lastName("User")
                        .password("EncodedHashMock123")
                        .role(Role.USER)
                        .enabled(true)
                        .locked(false)
                        .build())
        );

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
    @DisplayName("Security: Missing credentials on conversations endpoint returns 401 Unauthorized")
    public void testConversations_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/conversations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Authenticated principal unresolvable in database returns 403 Forbidden")
    public void testAuthenticated_UnresolvablePrincipal_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/conversations")
                .with(user("unregistered.ghost@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security Remediation: Never creates a user automatically on 401 or 403 access")
    public void testZeroUserCreationOnUnauthenticatedOrForbidden() throws Exception {
        long userCountBefore = userRepository.count();

        // 1. Unauthenticated request
        mockMvc.perform(post("/api/v1/assistant/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"Exploit attempt\"}"))
                .andExpect(status().isUnauthorized());

        // 2. Unregistered principal request
        mockMvc.perform(post("/api/v1/assistant/chat")
                .with(user("intruder@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"Exploit attempt\"}"))
                .andExpect(status().isForbidden());

        long userCountAfter = userRepository.count();
        assertThat(userCountAfter).isEqualTo(userCountBefore);
        assertThat(userRepository.findByEmail("intruder@example.com")).isEmpty();
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

    @Test
    @DisplayName("AI Integration: Primary versioned endpoint invokes AI client and returns fallbackMode: false")
    public void testSendMessage_AiSuccess_ReturnsFallbackModeFalse() throws Exception {
        AssistantConversationEntity conversation = conversationRepository.save(
                AssistantConversationEntity.builder()
                        .user(testUser)
                        .title("AI Test Session")
                        .build()
        );

        Map<String, Object> aiSuccessRes = new HashMap<>();
        aiSuccessRes.put("response", "AI generated recommendation for you.");
        when(aiClient.chat(any())).thenReturn(aiSuccessRes);

        mockMvc.perform(post("/api/v1/assistant/conversations/" + conversation.getId() + "/messages")
                .with(user("user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Find me high performance laptops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("AI generated recommendation for you."))
                .andExpect(jsonPath("$.fallbackMode").value(false));
    }

    @Test
    @DisplayName("AI Fallback: AI timeout or exception triggers deterministic fallback with fallbackMode: true")
    public void testSendMessage_AiFailure_GracefulFallback_ReturnsFallbackModeTrue() throws Exception {
        AssistantConversationEntity conversation = conversationRepository.save(
                AssistantConversationEntity.builder()
                        .user(testUser)
                        .title("Fallback Test Session")
                        .build()
        );

        when(aiClient.chat(any())).thenThrow(new RuntimeException("Simulated upstream connection timeout after 5000ms"));

        mockMvc.perform(post("/api/v1/assistant/conversations/" + conversation.getId() + "/messages")
                .with(user("user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Find me high performance laptops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallbackMode").value(true))
                .andExpect(jsonPath("$.response").isNotEmpty());
    }

    @Test
    @DisplayName("AI Fallback: Empty AI response triggers deterministic fallback with fallbackMode: true")
    public void testSendMessage_AiEmptyResponse_GracefulFallback_ReturnsFallbackModeTrue() throws Exception {
        AssistantConversationEntity conversation = conversationRepository.save(
                AssistantConversationEntity.builder()
                        .user(testUser)
                        .title("Blank AI Test Session")
                        .build()
        );

        when(aiClient.chat(any())).thenReturn(Map.of("response", "   "));

        mockMvc.perform(post("/api/v1/assistant/conversations/" + conversation.getId() + "/messages")
                .with(user("user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Find me high performance laptops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallbackMode").value(true))
                .andExpect(jsonPath("$.response").isNotEmpty());
    }
}
