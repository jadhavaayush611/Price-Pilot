package com.pricepilot.ai.stream;

import com.pricepilot.ai.AiClient;
import com.pricepilot.intelligence.assistant.model.AssistantConversationEntity;
import com.pricepilot.intelligence.assistant.repository.AssistantConversationRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AssistantStreamControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssistantConversationRepository conversationRepository;

    @Autowired
    private com.pricepilot.intelligence.assistant.repository.AssistantMessageRepository messageRepository;

    @MockitoBean
    private AiClient aiClient;

    private UserEntity userA;
    private UserEntity userB;
    private AssistantConversationEntity conversationA;

    @BeforeEach
    void setUp() {
        tearDown();

        userA = userRepository.save(UserEntity.builder()
                .email("user.a@example.com")
                .firstName("Alice")
                .lastName("Shopper")
                .password("SecureHashMock1")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build());

        userB = userRepository.save(UserEntity.builder()
                .email("user.b@example.com")
                .firstName("Bob")
                .lastName("Intruder")
                .password("SecureHashMock2")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build());

        conversationA = conversationRepository.save(
                AssistantConversationEntity.builder()
                        .user(userA)
                        .title("Alice's Laptop Search")
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        try {
            messageRepository.deleteAll();
            conversationRepository.deleteAll();
            userRepository.findByEmail("user.a@example.com").ifPresent(userRepository::delete);
            userRepository.findByEmail("user.b@example.com").ifPresent(userRepository::delete);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Controller: Authenticated user streaming valid conversation emits message_start, evidence, and Work Unit 1 explicit interrupted event without empty assistant placeholder")
    void testStream_AuthenticatedValidConversation() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/assistant/conversations/" + conversationA.getId() + "/stream")
                        .with(user("user.a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Find me best gaming laptops\"}"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        // Dispatch async to capture events
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        String content = mvcResult.getResponse().getContentAsString();
        assertThat(content).contains("event:message_start");
        assertThat(content).contains("event:evidence");
        // Work Unit 1 explicit behavior: upstream generation is deferred to WU2, emitting message_interrupted
        assertThat(content).contains("event:message_interrupted");
        assertThat(content).contains("\"reason\":\"UPSTREAM_GENERATION_UNAVAILABLE\"");
        assertThat(content).contains("\"tokensEmitted\":0");
        assertThat(content).doesNotContain("event:message_complete");
        assertThat(content).contains("\"protocolVersion\":\"v1.4\"");

        // Database verification: Only user's message is persisted; NO empty assistant message placeholder exists
        var messages = messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationA.getId());
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(com.pricepilot.intelligence.assistant.model.MessageRole.USER);
        assertThat(messages.get(0).getContent()).isEqualTo("Find me best gaming laptops");
    }

    @Test
    @DisplayName("Controller: Unauthenticated request returns 401 Unauthorized before streaming starts")
    void testStream_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/conversations/" + conversationA.getId() + "/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Find me laptops\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Controller: Invalid conversation ID (non-UUID) returns 400 Bad Request before streaming starts")
    void testStream_InvalidConversationId_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/conversations/not-a-valid-uuid/stream")
                        .with(user("user.a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Find me laptops\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Controller: Blank or invalid message content returns 400 Bad Request before streaming starts")
    void testStream_InvalidRequestBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/conversations/" + conversationA.getId() + "/stream")
                        .with(user("user.a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Controller: Nonexistent conversation ID returns 404 Not Found before streaming starts")
    void testStream_NonexistentConversation_Returns404() throws Exception {
        UUID nonexistentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/assistant/conversations/" + nonexistentId + "/stream")
                        .with(user("user.a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Find me laptops\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Controller: Conversation owned by another user returns 403 Forbidden before streaming starts")
    void testStream_ConversationOwnedByAnotherUser_Returns403() throws Exception {
        // User B attempting to stream User A's conversation
        mockMvc.perform(post("/api/v1/assistant/conversations/" + conversationA.getId() + "/stream")
                        .with(user("user.b@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Eavesdropping on Alice!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Compatibility: Existing synchronous endpoint remains 100% operational")
    void testExistingSynchronousEndpointRemainsOperational() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/conversations/" + conversationA.getId() + "/messages")
                        .with(user("user.a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Synchronous test message\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationA.getId().toString()))
                .andExpect(jsonPath("$.response").isNotEmpty());
    }
}
