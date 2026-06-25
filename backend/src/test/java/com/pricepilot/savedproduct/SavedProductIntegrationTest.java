package com.pricepilot.savedproduct;

import com.pricepilot.exception.DuplicateSaveException;
import com.pricepilot.exception.ProductArchivedException;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.savedproduct.dto.SavedProductResponseDTO;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class SavedProductIntegrationTest {

    @Autowired
    private SavedProductService savedProductService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private SavedProductRepository savedProductRepository;

    private UserEntity testUser;
    private ProductEntity activeProduct;
    private ProductEntity archivedProduct;
    private SellerEntity testSeller;

    @BeforeEach
    void setUp() {
        // Clean up to ensure isolation
        savedProductRepository.deleteAll();
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        sellerRepository.deleteAll();

        // Create and save test user
        testUser = UserEntity.builder()
                .email("integration-test@example.com")
                .firstName("Integration")
                .lastName("Test")
                .password("password123")
                .role(Role.USER)
                .enabled(true)
                .build();
        testUser = userRepository.save(testUser);

        // Create and save active product
        activeProduct = ProductEntity.builder()
                .name("Integration Test Product")
                .brand("Brand Auto")
                .category("Category Auto")
                .archived(false)
                .build();
        activeProduct = productRepository.save(activeProduct);

        // Create and save archived product
        archivedProduct = ProductEntity.builder()
                .name("Archived Integration Product")
                .brand("Brand Old")
                .category("Category Old")
                .archived(true)
                .build();
        archivedProduct = productRepository.save(archivedProduct);

        // Create and save seller
        testSeller = SellerEntity.builder()
                .name("Test Seller")
                .websiteUrl("http://testseller.com")
                .build();
        testSeller = sellerRepository.save(testSeller);

        // Create product price for active product
        ProductPriceEntity price = ProductPriceEntity.builder()
                .product(activeProduct)
                .seller(testSeller)
                .originalPrice(new BigDecimal("100.00"))
                .currentPrice(new BigDecimal("80.00"))
                .build();
        productPriceRepository.save(price);
    }

    @Test
    void testSaveProduct_Success() {
        savedProductService.saveProduct(testUser.getEmail(), activeProduct.getId());

        SavedProductId savedProductId = new SavedProductId(testUser.getId(), activeProduct.getId());
        assertTrue(savedProductRepository.existsById(savedProductId));

        List<SavedProductResponseDTO> savedProducts = savedProductService.getSavedProducts(testUser.getEmail());
        assertEquals(1, savedProducts.size());
        assertEquals("Integration Test Product", savedProducts.get(0).getName());
        assertEquals(new BigDecimal("80.00"), savedProducts.get(0).getBestPrice());
    }

    @Test
    void testSaveProduct_ArchivedThrowsException() {
        assertThrows(ProductArchivedException.class, () -> {
            savedProductService.saveProduct(testUser.getEmail(), archivedProduct.getId());
        });
    }

    @Test
    void testSaveProduct_DuplicateThrowsException() {
        savedProductService.saveProduct(testUser.getEmail(), activeProduct.getId());

        assertThrows(DuplicateSaveException.class, () -> {
            savedProductService.saveProduct(testUser.getEmail(), activeProduct.getId());
        });
    }

    @Test
    void testRemoveProduct_Success() {
        savedProductService.saveProduct(testUser.getEmail(), activeProduct.getId());
        SavedProductId savedProductId = new SavedProductId(testUser.getId(), activeProduct.getId());
        assertTrue(savedProductRepository.existsById(savedProductId));

        savedProductService.removeProduct(testUser.getEmail(), activeProduct.getId());
        assertFalse(savedProductRepository.existsById(savedProductId));
    }

    @Test
    void testRemoveProduct_NotFoundThrowsException() {
        assertThrows(ResourceNotFoundException.class, () -> {
            savedProductService.removeProduct(testUser.getEmail(), activeProduct.getId());
        });
    }
}
