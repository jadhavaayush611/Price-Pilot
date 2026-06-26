package com.pricepilot.watchlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.dto.CreateWatchlistRequestDTO;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class WatchlistIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private PriceWatchlistRepository watchlistRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserEntity testUser;
    private ProductEntity activeProduct;
    private SellerEntity testSeller;

    @BeforeEach
    void setUp() {
        watchlistRepository.deleteAll();
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        sellerRepository.deleteAll();

        testUser = UserEntity.builder()
                .email("integration-test@example.com")
                .firstName("Integration")
                .lastName("Test")
                .password("password123")
                .role(Role.USER)
                .enabled(true)
                .build();
        testUser = userRepository.save(testUser);

        activeProduct = ProductEntity.builder()
                .name("Integration Test Product")
                .brand("Brand Auto")
                .category("Category Auto")
                .archived(false)
                .build();
        activeProduct = productRepository.save(activeProduct);

        testSeller = SellerEntity.builder()
                .name("Test Seller")
                .websiteUrl("http://testseller.com")
                .build();
        testSeller = sellerRepository.save(testSeller);

        ProductPriceEntity price = ProductPriceEntity.builder()
                .product(activeProduct)
                .seller(testSeller)
                .currentPrice(new BigDecimal("1099.00"))
                .originalPrice(new BigDecimal("1299.00"))
                .discountPercentage(new BigDecimal("15.4"))
                .productUrl("http://testseller.com/product")
                .lastUpdated(java.time.LocalDateTime.now())
                .build();
        productPriceRepository.save(price);
    }

    @Test
    @WithMockUser(username = "integration-test@example.com", roles = "USER")
    void testCreateWatchlist_InvalidPrice_ReturnsStructuredError() throws Exception {
        CreateWatchlistRequestDTO request = CreateWatchlistRequestDTO.builder()
                .productId(activeProduct.getId())
                .targetPrice(new BigDecimal("1200.00")) // higher than best price 1099.00
                .build();

        mockMvc.perform(post("/api/v1/watchlists")
                        .with(user("integration-test@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("INVALID_TARGET_PRICE"))
                .andExpect(jsonPath("$.message").value("Target price must be less than the current best price."))
                .andExpect(jsonPath("$.details").exists())
                .andExpect(jsonPath("$.details.currentBestPrice").value(1099.0))
                .andExpect(jsonPath("$.details.currency").value("USD"));
    }
}
