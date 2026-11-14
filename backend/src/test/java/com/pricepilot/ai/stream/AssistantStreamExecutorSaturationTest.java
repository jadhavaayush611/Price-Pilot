package com.pricepilot.ai.stream;

import com.pricepilot.ai.AiClient;
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
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AssistantStreamExecutorSaturationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssistantConversationRepository conversationRepository;

    @Autowired
    private com.pricepilot.intelligence.assistant.repository.AssistantMessageRepository messageRepository;

    @MockitoBean(name = "assistantStreamExecutor")
    private ThreadPoolTaskExecutor assistantStreamExecutor;

    @MockitoBean
    private AiClient aiClient;

    private UserEntity testUser;
    private AssistantConversationEntity testConversation;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByEmail("user.sat@example.com").orElseGet(() ->
                userRepository.save(UserEntity.builder()
                        .email("user.sat@example.com")
                        .firstName("Saturation")
                        .lastName("Tester")
                        .password("PassMock123")
                        .role(Role.USER)
                        .enabled(true)
                        .locked(false)
                        .build())
        );

        testConversation = conversationRepository.save(
                AssistantConversationEntity.builder()
                        .user(testUser)
                        .title("Saturation Test Conv")
                        .build()
        );
    }

    @Test
    @DisplayName("Controller: Executor saturation returns 503 Service Unavailable with Retry-After header")
    void testExecutorSaturation_Returns503() throws Exception {
        doThrow(new RejectedExecutionException("ThreadPool saturated")).when(assistantStreamExecutor).execute(any(Runnable.class));

        mockMvc.perform(post("/api/v1/assistant/conversations/" + testConversation.getId() + "/stream")
                        .with(user("user.sat@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Find me high load laptops\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("STREAM_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Assistant stream capacity reached. Please retry in a few seconds."));

        // Verify that no orphaned messages were persisted in the database upon executor rejection
        org.assertj.core.api.Assertions.assertThat(
                messageRepository.findAllByConversationIdOrderByCreatedAtAsc(testConversation.getId())
        ).isEmpty();
    }
}
