package com.pricepilot.intelligence.recommendation;

import com.pricepilot.intelligence.recommendation.entity.RecommendationHistoryEventEntity;
import com.pricepilot.intelligence.recommendation.repository.RecommendationHistoryEventRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendationHistoryEventEntityMappingTest {

    @Autowired
    private RecommendationHistoryEventRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Should persist and retrieve RecommendationHistoryEventEntity with Double score and confidence")
    void shouldPersistAndRetrieveRecommendationHistoryEvent() {
        UUID userId = UUID.randomUUID();
        UUID targetProductId = UUID.randomUUID();
        UUID recommendedProductId = UUID.randomUUID();
        Double score = 94.75;
        Double confidence = 0.8950;

        RecommendationHistoryEventEntity entity = new RecommendationHistoryEventEntity(
                null,
                userId,
                "session-abc-123",
                targetProductId,
                recommendedProductId,
                targetProductId + "," + recommendedProductId,
                "SIMILAR",
                "DEFAULT_COMPARISON_SCORER",
                "TEMPLATE_REASONING_ENGINE",
                score,
                confidence,
                LocalDateTime.now()
        );

        RecommendationHistoryEventEntity saved = repository.saveAndFlush(entity);
        assertThat(saved.getId()).isNotNull();

        entityManager.clear();

        Optional<RecommendationHistoryEventEntity> retrievedOpt = repository.findById(saved.getId());
        assertThat(retrievedOpt).isPresent();

        RecommendationHistoryEventEntity retrieved = retrievedOpt.get();
        assertThat(retrieved.getUserId()).isEqualTo(userId);
        assertThat(retrieved.getSessionId()).isEqualTo("session-abc-123");
        assertThat(retrieved.getTargetProductId()).isEqualTo(targetProductId);
        assertThat(retrieved.getRecommendedProductId()).isEqualTo(recommendedProductId);
        assertThat(retrieved.getRecommendationType()).isEqualTo("SIMILAR");
        assertThat(retrieved.getScoringStrategy()).isEqualTo("DEFAULT_COMPARISON_SCORER");
        assertThat(retrieved.getExplanationStrategy()).isEqualTo("TEMPLATE_REASONING_ENGINE");
        assertThat(retrieved.getScore()).isEqualTo(94.75);
        assertThat(retrieved.getConfidence()).isEqualTo(0.8950);
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }

    @ParameterizedTest(name = "Persist valid score={0}, confidence={1}")
    @CsvSource({
            "0.0, 0.0",
            "100.0, 1.0",
            "50.0, 0.5",
            "0.1234, 0.8950",
            "94.75, 0.9999"
    })
    @DisplayName("Should persist and reload representative and boundary score/confidence values")
    void shouldPersistAndReloadRepresentativeValues(double score, double confidence) {
        UUID recommendedProductId = UUID.randomUUID();

        RecommendationHistoryEventEntity entity = new RecommendationHistoryEventEntity(
                null,
                null,
                "session-boundary",
                null,
                recommendedProductId,
                recommendedProductId.toString(),
                "BOUNDARY_TEST",
                "TEST_SCORER",
                "TEST_EXPLAINER",
                score,
                confidence,
                LocalDateTime.now()
        );

        RecommendationHistoryEventEntity saved = repository.saveAndFlush(entity);
        entityManager.clear();

        Optional<RecommendationHistoryEventEntity> retrievedOpt = repository.findById(saved.getId());
        assertThat(retrievedOpt).isPresent();
        assertThat(retrievedOpt.get().getScore()).isEqualTo(score);
        assertThat(retrievedOpt.get().getConfidence()).isEqualTo(confidence);
    }

    @Test
    @DisplayName("Should query recommendation history events by user ID in descending order")
    void shouldQueryByUserIdOrderedByCreatedAtDesc() {
        UUID userId = UUID.randomUUID();
        UUID recommendedProd = UUID.randomUUID();

        RecommendationHistoryEventEntity event1 = new RecommendationHistoryEventEntity(
                null, userId, "s1", null, recommendedProd, "p1", "CHEAPER",
                "STRATEGY", "EXPLANATION", 80.0, 0.75, LocalDateTime.now().minusHours(2)
        );

        RecommendationHistoryEventEntity event2 = new RecommendationHistoryEventEntity(
                null, userId, "s2", null, recommendedProd, "p2", "BETTER_VALUE",
                "STRATEGY", "EXPLANATION", 90.0, 0.85, LocalDateTime.now().minusHours(1)
        );

        repository.saveAndFlush(event1);
        repository.saveAndFlush(event2);

        List<RecommendationHistoryEventEntity> userEvents = repository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(userEvents).hasSize(2);
        assertThat(userEvents.get(0).getSessionId()).isEqualTo("s2");
        assertThat(userEvents.get(1).getSessionId()).isEqualTo("s1");
    }

    @Test
    @DisplayName("Verify V1.19 migration script exists and contains ALTER TABLE and CHECK constraints")
    void verifyV119MigrationFile() throws Exception {
        String resourcePath = "db/migration/V1.19__reconcile_recommendation_history_events_schema.sql";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("V1.19 migration script must be present on classpath").isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("ALTER TABLE recommendation_history_events");
            assertThat(sql).contains("ALTER COLUMN score TYPE DOUBLE PRECISION USING score::DOUBLE PRECISION");
            assertThat(sql).contains("ALTER COLUMN confidence TYPE DOUBLE PRECISION USING confidence::DOUBLE PRECISION");
            assertThat(sql).contains("CONSTRAINT chk_rec_history_score_range CHECK (score >= 0.0 AND score <= 100.0)");
            assertThat(sql).contains("CONSTRAINT chk_rec_history_confidence_range CHECK (confidence >= 0.0 AND confidence <= 1.0)");
        }
    }

    @Test
    @DisplayName("Verify existing NUMERIC data survives DOUBLE PRECISION migration and CHECK constraints are enforced")
    void verifyMigrationPreservesDataAndEnforcesCheckConstraints() {
        String testTable = "test_rec_history_migration_" + UUID.randomUUID().toString().replace("-", "");

        // Step 1: Create table with original V1.12 NUMERIC schema
        jdbcTemplate.execute("CREATE TABLE " + testTable + " ("
                + "id UUID PRIMARY KEY, "
                + "score NUMERIC(6,2) NOT NULL, "
                + "confidence NUMERIC(5,4) NOT NULL"
                + ")");

        // Step 2: Insert existing representative data in original NUMERIC format
        UUID row1Id = UUID.randomUUID();
        UUID row2Id = UUID.randomUUID();
        UUID row3Id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, 94.75, 0.8950)", row1Id);
        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, 0.00, 0.0000)", row2Id);
        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, 100.00, 1.0000)", row3Id);

        // Step 3: Apply the exact V1.19 migration DDL
        jdbcTemplate.execute("ALTER TABLE " + testTable + " ALTER COLUMN score TYPE DOUBLE PRECISION USING score::DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE " + testTable + " ALTER COLUMN confidence TYPE DOUBLE PRECISION USING confidence::DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE " + testTable + " ADD CONSTRAINT chk_" + testTable + "_score CHECK (score >= 0.0 AND score <= 100.0)");
        jdbcTemplate.execute("ALTER TABLE " + testTable + " ADD CONSTRAINT chk_" + testTable + "_conf CHECK (confidence >= 0.0 AND confidence <= 1.0)");

        // Step 4: Verify existing data converted losslessly
        Double score1 = jdbcTemplate.queryForObject("SELECT score FROM " + testTable + " WHERE id = ?", Double.class, row1Id);
        Double conf1 = jdbcTemplate.queryForObject("SELECT confidence FROM " + testTable + " WHERE id = ?", Double.class, row1Id);
        assertThat(score1).isEqualTo(94.75);
        assertThat(conf1).isEqualTo(0.8950);

        Double score2 = jdbcTemplate.queryForObject("SELECT score FROM " + testTable + " WHERE id = ?", Double.class, row2Id);
        Double conf2 = jdbcTemplate.queryForObject("SELECT confidence FROM " + testTable + " WHERE id = ?", Double.class, row2Id);
        assertThat(score2).isEqualTo(0.0);
        assertThat(conf2).isEqualTo(0.0);

        Double score3 = jdbcTemplate.queryForObject("SELECT score FROM " + testTable + " WHERE id = ?", Double.class, row3Id);
        Double conf3 = jdbcTemplate.queryForObject("SELECT confidence FROM " + testTable + " WHERE id = ?", Double.class, row3Id);
        assertThat(score3).isEqualTo(100.0);
        assertThat(conf3).isEqualTo(1.0);

        // Step 5: Verify invalid score below 0.0 is rejected by check constraint
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, -0.01, 0.5)", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Step 6: Verify invalid score above 100.0 is rejected by check constraint
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, 100.01, 0.5)", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Step 7: Verify invalid confidence below 0.0 is rejected by check constraint
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, 50.0, -0.0001)", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Step 8: Verify invalid confidence above 1.0 is rejected by check constraint
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, 50.0, 1.0001)", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup test table
        jdbcTemplate.execute("DROP TABLE " + testTable);
    }
}
