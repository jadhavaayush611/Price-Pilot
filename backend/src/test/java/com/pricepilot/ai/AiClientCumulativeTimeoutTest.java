package com.pricepilot.ai;

import com.pricepilot.intelligence.assistant.dto.AssistantResponseDTO;
import com.pricepilot.intelligence.assistant.dto.CreateConversationRequest;
import com.pricepilot.intelligence.assistant.dto.SendMessageRequest;
import com.pricepilot.intelligence.assistant.fallback.DeterministicShoppingAssistantFallback;
import com.pricepilot.intelligence.assistant.service.ShoppingAssistantService;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AiClientCumulativeTimeoutTest {

    private static HttpServer mockServer;
    private static int mockPort;

    private static final AtomicBoolean serverDetectedClientAbort = new AtomicBoolean(false);
    private static final CountDownLatch abortLatch = new CountDownLatch(1);
    private static final AtomicInteger slowTrickleChunksDelivered = new AtomicInteger(0);

    @Autowired
    private AiClient aiClient;

    @Autowired
    private ShoppingAssistantService shoppingAssistantService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private DeterministicShoppingAssistantFallback fallbackGenerator;

    @BeforeAll
    static void startMockServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockPort = mockServer.getAddress().getPort();

        // 1. Normal fast AI response endpoint
        mockServer.createContext("/success/assistant/chat", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String responseJson = "{\"response\": \"Authoritative laptop recommendation for developer workloads.\", \"conversationId\": \"c1\"}";
                byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        // 2. Silent upstream endpoint (completely silent for 7.0 seconds)
        mockServer.createContext("/silent/assistant/chat", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    Thread.sleep(7000);
                    byte[] bytes = "{\"response\":\"too late\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } catch (Exception ignored) {
                } finally {
                    exchange.close();
                }
            }
        });

        // 3. Slow-trickle upstream endpoint (drips bytes every 1.5s for 5 iterations = 7.5s total)
        mockServer.createContext("/trickle/assistant/chat", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                // Chunked transfer encoding
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                try {
                    for (int i = 1; i <= 5; i++) {
                        os.write(("{\"chunk\": " + i + "} \n").getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        slowTrickleChunksDelivered.incrementAndGet();
                        Thread.sleep(1500);
                    }
                    os.close();
                } catch (Exception e) {
                    // Client cancelled or aborted connection
                    serverDetectedClientAbort.set(true);
                    abortLatch.countDown();
                } finally {
                    exchange.close();
                }
            }
        });

        mockServer.start();
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    private void configureAiUrl(String subPath) throws Exception {
        Field urlField = AiClientImpl.class.getDeclaredField("aiUrl");
        urlField.setAccessible(true);
        urlField.set(aiClient, "http://127.0.0.1:" + mockPort + "/" + subPath);
    }

    @Test
    @DisplayName("Normal upstream response completes successfully under deadline")
    void testNormalResponseSuccess() throws Exception {
        configureAiUrl("success");

        long start = System.currentTimeMillis();
        Map<String, Object> result = aiClient.chat(Map.of("message", "Best laptops"));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(result.get("response")).isEqualTo("Authoritative laptop recommendation for developer workloads.");
        assertThat(elapsed).isLessThan(4500);
    }

    @Test
    @DisplayName("Silent upstream times out within the configured deadline budget")
    void testSilentUpstreamTimesOut() throws Exception {
        configureAiUrl("silent");

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> aiClient.chat(Map.of("message", "Silent query")))
                .isInstanceOf(RuntimeException.class);
        long elapsed = System.currentTimeMillis() - start;

        // Deadline is 5000ms. Allowed scheduler tolerance 4800ms - 5800ms.
        assertThat(elapsed).isGreaterThanOrEqualTo(4800);
        assertThat(elapsed).isLessThanOrEqualTo(5800);
    }

    @Test
    @DisplayName("Slow-trickle upstream cannot extend the total request beyond the 5,000 ms budget")
    void testSlowTrickleCannotExtendBeyondDeadline() throws Exception {
        configureAiUrl("trickle");
        serverDetectedClientAbort.set(false);
        slowTrickleChunksDelivered.set(0);

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> aiClient.chat(Map.of("message", "Trickle query")))
                .isInstanceOf(RuntimeException.class);
        long elapsed = System.currentTimeMillis() - start;

        // Upstream was scheduled for 7.5 seconds, but client cut it off at ~5.0 seconds
        assertThat(elapsed).isGreaterThanOrEqualTo(4800);
        assertThat(elapsed).isLessThanOrEqualTo(5800);
        // Verify server could not deliver all 5 chunks
        assertThat(slowTrickleChunksDelivered.get()).isLessThan(5);
    }

    @Test
    @DisplayName("Timed-out request is actively aborted and cancelled at the socket/transport layer")
    void testTimedOutRequestIsActivelyCancelledOnServer() throws Exception {
        configureAiUrl("trickle");

        assertThatThrownBy(() -> aiClient.chat(Map.of("message", "Cancel check")))
                .isInstanceOf(RuntimeException.class);

        // Await confirmation that the server thread encountered an IOException writing to the cancelled socket
        boolean aborted = abortLatch.await(3, TimeUnit.SECONDS) || serverDetectedClientAbort.get();
        assertThat(aborted)
                .as("Server-side HTTP exchange should detect connection reset / abort when client deadline expires")
                .isTrue();
    }

    @Test
    @DisplayName("Timeout triggers deterministic fallback exactly once and sets fallbackMode: true")
    void testShoppingAssistantServiceFallbackOnTimeout() throws Exception {
        configureAiUrl("trickle");

        UserEntity user = userRepository.save(UserEntity.builder()
                .email("timeout-test-" + System.currentTimeMillis() + "@example.com")
                .password("TestPassword123!")
                .firstName("Timeout")
                .lastName("User")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build());

        var conv = shoppingAssistantService.createConversation(
                user.getId(),
                new CreateConversationRequest("Timeout test", null)
        );

        org.mockito.Mockito.reset(fallbackGenerator);

        long start = System.currentTimeMillis();
        AssistantResponseDTO response = shoppingAssistantService.sendMessage(
                conv.getId(),
                user.getId(),
                new SendMessageRequest("Find high performance laptops", null)
        );
        long elapsed = System.currentTimeMillis() - start;

        // Bounded by deadline + fallback generation
        assertThat(elapsed).isGreaterThanOrEqualTo(4800);
        assertThat(elapsed).isLessThanOrEqualTo(6500);

        // Assert fallback mode
        assertThat(response).isNotNull();
        assertThat(response.getFallbackMode()).isTrue();
        assertThat(response.getResponse()).isNotBlank();

        // Verify fallback was invoked exactly once
        verify(fallbackGenerator, times(1)).generateFallbackResponse(any(), any(), any());
    }
}
