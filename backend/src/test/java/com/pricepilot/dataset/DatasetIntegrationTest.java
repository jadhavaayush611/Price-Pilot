package com.pricepilot.dataset;

import com.pricepilot.analytics.ProductAnalyticsEntity;
import com.pricepilot.analytics.ProductAnalyticsRepository;
import com.pricepilot.interaction.InteractionType;
import com.pricepilot.interaction.UserInteractionEventEntity;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.pricehistory.PriceHistoryEntity;
import com.pricepilot.pricehistory.PriceHistoryRepository;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.savedproduct.SavedProductId;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DatasetIntegrationTest {

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

    @Autowired
    private SavedProductRepository savedProductRepository;

    @Autowired
    private ProductAnalyticsRepository analyticsRepository;

    @Autowired
    private UserInteractionEventRepository eventRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    private UserEntity testAdminUser;
    private UserEntity testStandardUser;
    private ProductEntity activeProduct;
    private SellerEntity testSeller;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        watchlistRepository.deleteAll();
        savedProductRepository.deleteAll();
        analyticsRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        sellerRepository.deleteAll();

        // 1. Create Users
        testAdminUser = UserEntity.builder()
                .email("admin@example.com")
                .firstName("Admin")
                .lastName("User")
                .password("admin123")
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        testAdminUser = userRepository.save(testAdminUser);

        testStandardUser = UserEntity.builder()
                .email("user@example.com")
                .firstName("Standard")
                .lastName("User")
                .password("user123")
                .role(Role.USER)
                .enabled(true)
                .build();
        testStandardUser = userRepository.save(testStandardUser);

        // 2. Create Products
        activeProduct = ProductEntity.builder()
                .name("Dataset Test Product")
                .brand("Brand Dataset")
                .category("Category Dataset")
                .archived(false)
                .build();
        activeProduct = productRepository.save(activeProduct);

        // 3. Create Sellers
        testSeller = SellerEntity.builder()
                .name("Dataset Seller")
                .websiteUrl("http://datasetseller.com")
                .build();
        testSeller = sellerRepository.save(testSeller);

        // 4. Create Prices
        ProductPriceEntity price = ProductPriceEntity.builder()
                .product(activeProduct)
                .seller(testSeller)
                .currentPrice(new BigDecimal("99.00"))
                .originalPrice(new BigDecimal("120.00"))
                .productUrl("http://datasetseller.com/product")
                .lastUpdated(LocalDateTime.now())
                .build();
        price = productPriceRepository.save(price);
        activeProduct.getProductPrices().add(price);
        activeProduct = productRepository.save(activeProduct);

        // 5. Create Analytics
        ProductAnalyticsEntity analytics = ProductAnalyticsEntity.builder()
                .product(activeProduct)
                .viewCount(10L)
                .saveCount(5L)
                .watchlistCount(2L)
                .priceChangeCount(1L)
                .lastViewedAt(LocalDateTime.now())
                .build();
        analyticsRepository.save(analytics);

        // 6. Create Interaction Events
        UserInteractionEventEntity viewEvent = UserInteractionEventEntity.builder()
                .user(testStandardUser)
                .product(activeProduct)
                .interactionType(InteractionType.PRODUCT_VIEW)
                .metadata(Map.of())
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(viewEvent);

        UserInteractionEventEntity searchEvent = UserInteractionEventEntity.builder()
                .user(testStandardUser)
                .interactionType(InteractionType.SEARCH)
                .metadata(Map.of("keyword", "dataset"))
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(searchEvent);

        // 7. Create Watchlist
        PriceWatchlistEntity watchlist = PriceWatchlistEntity.builder()
                .user(testStandardUser)
                .product(activeProduct)
                .targetPrice(new BigDecimal("80.00"))
                .currentBestPrice(new BigDecimal("99.00"))
                .active(true)
                .build();
        watchlistRepository.save(watchlist);

        // 8. Create Saved Product
        SavedProductEntity savedProduct = SavedProductEntity.builder()
                .id(new SavedProductId(testStandardUser.getId(), activeProduct.getId()))
                .user(testStandardUser)
                .product(activeProduct)
                .build();
        savedProductRepository.save(savedProduct);

        // 9. Create Price History
        PriceHistoryEntity priceHistory = PriceHistoryEntity.builder()
                .product(activeProduct)
                .seller(testSeller)
                .oldPrice(new BigDecimal("100.00"))
                .newPrice(new BigDecimal("99.00"))
                .priceDifference(new BigDecimal("-1.00"))
                .changePercentage(new BigDecimal("-1.00"))
                .changedAt(LocalDateTime.now())
                .build();
        priceHistoryRepository.save(priceHistory);
    }

    @Test
    void testProductsDataset_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/products")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Dataset Test Product"))
                .andExpect(jsonPath("$.content[0].brand").value("Brand Dataset"))
                .andExpect(jsonPath("$.content[0].category").value("Category Dataset"))
                .andExpect(jsonPath("$.content[0].currentMinPrice").value(99.00))
                .andExpect(jsonPath("$.content[0].sellerCount").value(1));
    }

    @Test
    void testProductsDataset_AsUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/products")
                        .with(user("user@example.com").roles("USER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void testProductsDataset_AsUnauthenticated_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testProductsDataset_CsvExport_Success() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/products")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(containsString("id,name,brand,category,description,archived,createdAt,updatedAt,currentMinPrice,currentMaxPrice,originalMinPrice,originalMaxPrice,averageDiscountPercentage,sellerCount,averageSellerRating")))
                .andExpect(content().string(containsString("Dataset Test Product,Brand Dataset,Category Dataset")));
    }

    @Test
    void testProductAnalyticsDataset_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/product-analytics")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].viewCount").value(10))
                .andExpect(jsonPath("$.content[0].saveCount").value(5))
                .andExpect(jsonPath("$.content[0].trendingScore").value(57.0));
    }

    @Test
    void testInteractionEventsDataset_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/interaction-events")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void testWatchlistsDataset_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/watchlists")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].targetPrice").value(80.00));
    }

    @Test
    void testSavedProductsDataset_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/saved-products")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].productId").value(activeProduct.getId().toString()));
    }

    @Test
    void testSearchHistoryDataset_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/search-history")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].keyword").value("dataset"));
    }

    @Test
    void testDashboardSummaryDataset_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/dashboard-summary")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void testPriceHistoryDataset_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/price-history")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].oldPrice").value(100.00));
    }
}
