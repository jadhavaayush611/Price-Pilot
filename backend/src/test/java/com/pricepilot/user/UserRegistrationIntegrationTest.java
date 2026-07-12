package com.pricepilot.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.user.dto.UserRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class UserRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();
    }

    @Test
    public void testSuccessfulRegistrationAndEmailNormalization() throws Exception {
        UserRequestDTO request = UserRequestDTO.builder()
                .email("  AbC@gmAil.cOm  ")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email", is("abc@gmail.com")))
                .andExpect(jsonPath("$.user.firstName", is("John")))
                .andExpect(jsonPath("$.user.lastName", is("Doe")));

        // Verify it was saved as lowercase in DB
        assert userRepository.existsByEmail("abc@gmail.com");
        assert userRepository.existsByEmail("ABC@GMAIL.COM"); // Case-insensitive exists check
    }

    @Test
    public void testDuplicateRegistrationIsCaseInsensitive() throws Exception {
        UserRequestDTO request1 = UserRequestDTO.builder()
                .email("abc@gmail.com")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        UserRequestDTO request2 = UserRequestDTO.builder()
                .email("ABC@GMAIL.COM")
                .password("password123")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already in use")));
    }

    @Test
    public void testInvalidEmailDomainSyntaxRejected() throws Exception {
        UserRequestDTO request = UserRequestDTO.builder()
                .email("abc@com") // Missing a proper TLD/subdomain structure
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
