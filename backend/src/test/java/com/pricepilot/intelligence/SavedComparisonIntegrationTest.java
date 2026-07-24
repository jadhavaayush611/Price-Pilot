package com.pricepilot.intelligence;

import com.pricepilot.PricepilotApplication;
import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.SavedComparisonResponseDTO;
import com.pricepilot.intelligence.comparison.entity.SavedComparisonEntity;
import com.pricepilot.intelligence.comparison.repository.SavedComparisonRepository;
import com.pricepilot.intelligence.comparison.ComparisonService;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PricepilotApplication.class)
@ActiveProfiles("test")
@Transactional
class SavedComparisonIntegrationTest {

    @Autowired
    private ComparisonService comparisonService;

    @Autowired
    private SavedComparisonRepository savedComparisonRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity userA;
    private UserEntity userB;

    @BeforeEach
    void setUp() {
        savedComparisonRepository.deleteAll();

        userA = userRepository.save(UserEntity.builder()
                .email("usera_" + UUID.randomUUID() + "@example.com")
                .password("password123")
                .firstName("User")
                .lastName("A")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build());

        userB = userRepository.save(UserEntity.builder()
                .email("userb_" + UUID.randomUUID() + "@example.com")
                .password("password123")
                .firstName("User")
                .lastName("B")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build());
    }

    @Test
    @DisplayName("saveComparisonSession persists saved comparison to database")
    void testSaveComparisonSessionIntegration() {
        UUID prodId1 = UUID.randomUUID();
        UUID prodId2 = UUID.randomUUID();

        ComparisonRequest req = new ComparisonRequest(
                List.of(prodId1, prodId2), "Laptops", List.of("Price"), userA.getId(), null, "My Laptop Matrix", "Buying guide"
        );

        SavedComparisonResponseDTO saved = comparisonService.saveComparisonSession(userA.getId(), req);

        assertNotNull(saved.getId());
        assertEquals("My Laptop Matrix", saved.getName());
        assertEquals(2, saved.getProductIds().size());

        List<SavedComparisonEntity> entities = savedComparisonRepository.findByUserIdOrderByCreatedAtDesc(userA.getId());
        assertEquals(1, entities.size());
        assertEquals("My Laptop Matrix", entities.get(0).getName());
    }

    @Test
    @DisplayName("getSavedComparisons returns paginated results for owner only")
    void testGetSavedComparisonsPagination() {
        ComparisonRequest req = new ComparisonRequest(List.of(UUID.randomUUID()), "Tech", List.of(), userA.getId(), null, "Matrix A", null);
        comparisonService.saveComparisonSession(userA.getId(), req);

        Page<SavedComparisonResponseDTO> pageA = comparisonService.getSavedComparisons(userA.getId(), 0, 10);
        assertEquals(1, pageA.getTotalElements());

        Page<SavedComparisonResponseDTO> pageB = comparisonService.getSavedComparisons(userB.getId(), 0, 10);
        assertEquals(0, pageB.getTotalElements());
    }

    @Test
    @DisplayName("deleteSavedComparison validates ownership and deletes saved record")
    void testDeleteSavedComparisonOwnershipValidation() {
        ComparisonRequest req = new ComparisonRequest(List.of(UUID.randomUUID()), "Tech", List.of(), userA.getId(), null, "Matrix A", null);
        SavedComparisonResponseDTO saved = comparisonService.saveComparisonSession(userA.getId(), req);

        // User B attempts to delete User A's saved comparison
        assertThrows(AccessDeniedException.class, () -> comparisonService.deleteSavedComparison(userB.getId(), saved.getId()));

        // User A deletes own saved comparison
        comparisonService.deleteSavedComparison(userA.getId(), saved.getId());
        assertTrue(savedComparisonRepository.findById(saved.getId()).isEmpty());
    }
}
