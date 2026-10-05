package com.pricepilot.intelligence.personalization.preference;

import com.pricepilot.currency.CurrencyCode;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserShoppingPreferenceEntityMappingTest {

    @Autowired
    private UserShoppingPreferenceRepository preferenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserEntity createTestUser() {
        UserEntity user = UserEntity.builder()
                .email("pref-test-" + UUID.randomUUID() + "@example.com")
                .password("password123")
                .firstName("Preference")
                .lastName("Tester")
                .role(Role.USER)
                .enabled(true)
                .locked(false)
                .build();
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("Should persist, flush, and reload UserShoppingPreferenceEntity with Double minRating")
    void shouldPersistAndReloadShoppingPreference() {
        UserEntity user = createTestUser();
        Double minRating = 4.25;

        UserShoppingPreferenceEntity preference = UserShoppingPreferenceEntity.builder()
                .userId(user.getId())
                .user(user)
                .minBudget(BigDecimal.valueOf(100.00))
                .maxBudget(BigDecimal.valueOf(1500.00))
                .minRating(minRating)
                .dealSensitivity(DealSensitivity.HIGH)
                .priceSensitivity(PriceSensitivity.HIGH)
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .currency(CurrencyCode.INR)
                .preferredCategories(Set.of("Electronics", "Laptops"))
                .preferredBrands(Set.of("Apple", "Sony"))
                .build();

        UserShoppingPreferenceEntity saved = preferenceRepository.saveAndFlush(preference);
        assertThat(saved.getId()).isNotNull();

        entityManager.clear();

        Optional<UserShoppingPreferenceEntity> reloadedOpt = preferenceRepository.findById(saved.getId());
        assertThat(reloadedOpt).isPresent();

        UserShoppingPreferenceEntity reloaded = reloadedOpt.get();
        assertThat(reloaded.getUserId()).isEqualTo(user.getId());
        assertThat(reloaded.getMinRating()).isEqualTo(4.25);
        assertThat(reloaded.getMinBudget()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(reloaded.getMaxBudget()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));
        assertThat(reloaded.getCurrency()).isEqualTo(CurrencyCode.INR);
        assertThat(reloaded.getPreferredCategories()).containsExactlyInAnyOrder("Electronics", "Laptops");
        assertThat(reloaded.getPreferredBrands()).containsExactlyInAnyOrder("Apple", "Sony");
    }

    @Test
    @DisplayName("Should permit null minRating when user has not set a rating filter")
    void shouldPermitNullMinRating() {
        UserEntity user = createTestUser();

        UserShoppingPreferenceEntity preference = UserShoppingPreferenceEntity.builder()
                .userId(user.getId())
                .user(user)
                .minBudget(BigDecimal.valueOf(50.00))
                .maxBudget(BigDecimal.valueOf(500.00))
                .minRating(null)
                .dealSensitivity(DealSensitivity.LOW)
                .priceSensitivity(PriceSensitivity.LOW)
                .availabilityPreference(AvailabilityPreference.ALL)
                .currency(CurrencyCode.USD)
                .build();

        UserShoppingPreferenceEntity saved = preferenceRepository.saveAndFlush(preference);
        entityManager.clear();

        Optional<UserShoppingPreferenceEntity> reloadedOpt = preferenceRepository.findById(saved.getId());
        assertThat(reloadedOpt).isPresent();
        assertThat(reloadedOpt.get().getMinRating()).isNull();
    }

    @ParameterizedTest(name = "Persist valid minRating={0}")
    @CsvSource({
            "0.0",
            "5.0",
            "1.0",
            "3.5",
            "4.75"
    })
    @DisplayName("Should accept representative and boundary rating values [0.0, 5.0]")
    void shouldAcceptRepresentativeRatings(double rating) {
        UserEntity user = createTestUser();

        UserShoppingPreferenceEntity preference = UserShoppingPreferenceEntity.builder()
                .userId(user.getId())
                .user(user)
                .minRating(rating)
                .dealSensitivity(DealSensitivity.MEDIUM)
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.ALL)
                .currency(CurrencyCode.INR)
                .build();

        UserShoppingPreferenceEntity saved = preferenceRepository.saveAndFlush(preference);
        entityManager.clear();

        Optional<UserShoppingPreferenceEntity> reloadedOpt = preferenceRepository.findById(saved.getId());
        assertThat(reloadedOpt).isPresent();
        assertThat(reloadedOpt.get().getMinRating()).isEqualTo(rating);
    }

    @Test
    @DisplayName("Verify V1.20 migration contains ALTER TABLE for min_rating and CHECK constraint")
    void verifyV120MigrationForUserShoppingPreferences() throws Exception {
        String resourcePath = "db/migration/V1.20__reconcile_metadata_and_preference_schema.sql";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("V1.20 migration script must be on classpath").isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("ALTER TABLE user_shopping_preferences");
            assertThat(sql).contains("ALTER COLUMN min_rating TYPE DOUBLE PRECISION USING min_rating::DOUBLE PRECISION");
            assertThat(sql).contains("chk_user_pref_min_rating_range");
        }
    }

    @Test
    @DisplayName("Verify pre-existing NUMERIC min_rating data survives DOUBLE PRECISION migration and CHECK constraints are enforced")
    void verifyPreferenceDataPreservationAndCheckConstraints() {
        String testTable = "test_user_pref_mig_" + UUID.randomUUID().toString().replace("-", "");

        // Step 1: Create table with original V1.15 schema (NUMERIC(3, 2) nullable)
        jdbcTemplate.execute("CREATE TABLE " + testTable + " ("
                + "id UUID PRIMARY KEY, "
                + "user_id UUID NOT NULL, "
                + "min_rating NUMERIC(3, 2)"
                + ")");

        // Step 2: Insert representative legacy data
        UUID row1 = UUID.randomUUID();
        UUID row2 = UUID.randomUUID();
        UUID row3 = UUID.randomUUID();
        UUID row4 = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 4.50)", row1, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 0.00)", row2, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 5.00)", row3, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, NULL)", row4, UUID.randomUUID());

        // Step 3: Apply V1.20 DDL
        jdbcTemplate.execute("ALTER TABLE " + testTable + " ALTER COLUMN min_rating TYPE DOUBLE PRECISION USING min_rating::DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE " + testTable + " ADD CONSTRAINT chk_" + testTable + "_rating CHECK (min_rating IS NULL OR (min_rating >= 0.0 AND min_rating <= 5.0))");

        // Step 4: Verify data preserved losslessly
        assertThat(jdbcTemplate.queryForObject("SELECT min_rating FROM " + testTable + " WHERE id = ?", Double.class, row1)).isEqualTo(4.50);
        assertThat(jdbcTemplate.queryForObject("SELECT min_rating FROM " + testTable + " WHERE id = ?", Double.class, row2)).isEqualTo(0.0);
        assertThat(jdbcTemplate.queryForObject("SELECT min_rating FROM " + testTable + " WHERE id = ?", Double.class, row3)).isEqualTo(5.0);
        assertThat(jdbcTemplate.queryForObject("SELECT min_rating FROM " + testTable + " WHERE id = ?", Double.class, row4)).isNull();

        // Step 5: Verify out-of-bounds rejection (< 0.0 and > 5.0)
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, -0.01)", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 5.01)", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup
        jdbcTemplate.execute("DROP TABLE " + testTable);
    }
}
