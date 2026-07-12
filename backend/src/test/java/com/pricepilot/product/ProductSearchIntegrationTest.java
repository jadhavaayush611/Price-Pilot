package com.pricepilot.product;

import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ProductSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;

    @BeforeEach
    public void setup() {
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        sellerRepository.deleteAll();
        userRepository.deleteAll();

        // Setup a test user
        testUser = UserEntity.builder()
                .email("testuser@example.com")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build();
        userRepository.save(testUser);

        // Setup a seller
        SellerEntity seller = SellerEntity.builder()
                .name("Amazon")
                .websiteUrl("https://amazon.com")
                .build();
        sellerRepository.save(seller);

        // Setup products
        ProductEntity p1 = ProductEntity.builder()
                .name("iPhone 15 Pro")
                .brand("Apple")
                .category("Smartphone")
                .description("Super flagship smartphone from Apple")
                .archived(false)
                .build();
        productRepository.save(p1);

        ProductPriceEntity pp1 = ProductPriceEntity.builder()
                .product(p1)
                .seller(seller)
                .currentPrice(new BigDecimal("999.00"))
                .originalPrice(new BigDecimal("1099.00"))
                .productUrl("https://amazon.com/iphone15")
                .lastUpdated(LocalDateTime.now())
                .build();
        productPriceRepository.save(pp1);

        ProductEntity p2 = ProductEntity.builder()
                .name("MacBook Air M2")
                .brand("Apple")
                .category("Laptop")
                .description("Lightweight powerful laptop from Apple")
                .archived(false)
                .build();
        productRepository.save(p2);

        ProductPriceEntity pp2 = ProductPriceEntity.builder()
                .product(p2)
                .seller(seller)
                .currentPrice(new BigDecimal("1199.00"))
                .originalPrice(new BigDecimal("1299.00"))
                .productUrl("https://amazon.com/macbookair")
                .lastUpdated(LocalDateTime.now())
                .build();
        productPriceRepository.save(pp2);
    }

    @Test
    public void testSearchWithCategoryFilterAndDefaultSort() throws Exception {
        // Search matching "Apple", category "Laptop", with default sort
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "Apple")
                        .param("category", "Laptop")
                        .param("sort", "default")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("MacBook Air M2")))
                .andExpect(jsonPath("$.content[0].category", is("Laptop")));
    }

    @Test
    public void testSearchWithPriceAscSort() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("sort", "price-asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name", is("iPhone 15 Pro"))) // 999.00
                .andExpect(jsonPath("$.content[1].name", is("MacBook Air M2"))); // 1199.00
    }

    @Test
    public void testSearchWithPriceDescSort() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("sort", "price-desc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name", is("MacBook Air M2"))) // 1199.00
                .andExpect(jsonPath("$.content[1].name", is("iPhone 15 Pro"))); // 999.00
    }

    @Test
    public void testSearchWithInvalidSortRejected() throws Exception {
        // Invalid sort parameter should be rejected with 400 Bad Request
        mockMvc.perform(get("/api/v1/search")
                        .param("sort", "invalidField,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort property")));
    }

    @Test
    public void testRecommendationsEndpoint() throws Exception {
        // Get recommendations for testUser should return successfully
        mockMvc.perform(get("/api/v1/recommendations")
                        .with(user(testUser.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void testSimilarProductsEndpoint() throws Exception {
        // Find one product ID
        ProductEntity laptop = productRepository.findAll().stream()
                .filter(p -> p.getCategory().equals("Laptop"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/recommendations/similar/" + laptop.getId())
                        .with(user(testUser.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
