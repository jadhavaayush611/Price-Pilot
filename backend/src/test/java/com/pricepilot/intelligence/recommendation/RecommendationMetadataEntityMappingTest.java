package com.pricepilot.intelligence.recommendation;

import com.pricepilot.intelligence.recommendation.entity.RecommendationMetadataEntity;
import com.pricepilot.intelligence.recommendation.repository.RecommendationMetadataRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendationMetadataEntityMappingTest {

    @Autowired
    private RecommendationMetadataRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Should persist, flush, and reload RecommendationMetadataEntity with Double confidenceScore")
    void shouldPersistAndReloadRecommendationMetadata() {
        UUID productId = UUID.randomUUID();
        Double confidenceScore = 0.8950;

        RecommendationMetadataEntity entity = new RecommendationMetadataEntity(
                null,
                productId,
                "v2.1",
                "HYBRID_RANKING",
                "{\"price_weight\": 0.35}",
                confidenceScore,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        RecommendationMetadataEntity saved = repository.saveAndFlush(entity);
        assertThat(saved.getId()).isNotNull();

        entityManager.clear();

        Optional<RecommendationMetadataEntity> reloadedOpt = repository.findById(saved.getId());
        assertThat(reloadedOpt).isPresent();

        RecommendationMetadataEntity reloaded = reloadedOpt.get();
        assertThat(reloaded.getProductId()).isEqualTo(productId);
        assertThat(reloaded.getModelVersion()).isEqualTo("v2.1");
        assertThat(reloaded.getAlgorithmType()).isEqualTo("HYBRID_RANKING");
        assertThat(reloaded.getConfidenceScore()).isEqualTo(0.8950);
    }

    @Test
    @DisplayName("Should permit null confidenceScore for uncalibrated models")
    void shouldPermitNullConfidenceScore() {
        UUID productId = UUID.randomUUID();

        RecommendationMetadataEntity entity = new RecommendationMetadataEntity(
                null,
                productId,
                "v1.0-uncalibrated",
                "HEURISTIC",
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        RecommendationMetadataEntity saved = repository.saveAndFlush(entity);
        entityManager.clear();

        Optional<RecommendationMetadataEntity> reloadedOpt = repository.findById(saved.getId());
        assertThat(reloadedOpt).isPresent();
        assertThat(reloadedOpt.get().getConfidenceScore()).isNull();
    }

    @ParameterizedTest(name = "Persist valid confidence={0}")
    @CsvSource({
            "0.0",
            "1.0",
            "0.5",
            "0.1234",
            "0.8950",
            "0.9999"
    })
    @DisplayName("Should accept representative and boundary confidence values [0.0, 1.0]")
    void shouldAcceptRepresentativeConfidenceScores(double confidence) {
        UUID productId = UUID.randomUUID();

        RecommendationMetadataEntity entity = new RecommendationMetadataEntity(
                null,
                productId,
                "v2.0",
                "REPRESENTATIVE_TEST",
                "{}",
                confidence,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        RecommendationMetadataEntity saved = repository.saveAndFlush(entity);
        entityManager.clear();

        Optional<RecommendationMetadataEntity> reloadedOpt = repository.findById(saved.getId());
        assertThat(reloadedOpt).isPresent();
        assertThat(reloadedOpt.get().getConfidenceScore()).isEqualTo(confidence);
    }

    @Test
    @DisplayName("Verify V1.20 migration contains ALTER TABLE for confidence_score and CHECK constraint")
    void verifyV120MigrationForRecommendationMetadata() throws Exception {
        String resourcePath = "db/migration/V1.20__reconcile_metadata_and_preference_schema.sql";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("V1.20 migration script must be on classpath").isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("ALTER TABLE recommendation_metadata");
            assertThat(sql).contains("ALTER COLUMN confidence_score TYPE DOUBLE PRECISION USING confidence_score::DOUBLE PRECISION");
            assertThat(sql).contains("chk_rec_metadata_confidence_range");
        }
    }

    @Test
    @DisplayName("Verify pre-existing NUMERIC data survives DOUBLE PRECISION migration and CHECK constraints are enforced")
    void verifyMetadataDataPreservationAndCheckConstraints() {
        String testTable = "test_rec_meta_mig_" + UUID.randomUUID().toString().replace("-", "");

        // Step 1: Create table with original V1.11 schema (NUMERIC(5,4) nullable)
        jdbcTemplate.execute("CREATE TABLE " + testTable + " ("
                + "id UUID PRIMARY KEY, "
                + "product_id UUID NOT NULL, "
                + "model_version VARCHAR(50) NOT NULL, "
                + "confidence_score NUMERIC(5,4)"
                + ")");

        // Step 2: Insert representative legacy data
        UUID row1 = UUID.randomUUID();
        UUID row2 = UUID.randomUUID();
        UUID row3 = UUID.randomUUID();
        UUID row4 = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 'v1', 0.8950)", row1, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 'v1', 0.0000)", row2, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 'v1', 1.0000)", row3, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 'v1', NULL)", row4, UUID.randomUUID());

        // Step 3: Apply V1.20 DDL
        jdbcTemplate.execute("ALTER TABLE " + testTable + " ALTER COLUMN confidence_score TYPE DOUBLE PRECISION USING confidence_score::DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE " + testTable + " ADD CONSTRAINT chk_" + testTable + "_conf CHECK (confidence_score IS NULL OR (confidence_score >= 0.0 AND confidence_score <= 1.0))");

        // Step 4: Verify data preserved losslessly
        assertThat(jdbcTemplate.queryForObject("SELECT confidence_score FROM " + testTable + " WHERE id = ?", Double.class, row1)).isEqualTo(0.8950);
        assertThat(jdbcTemplate.queryForObject("SELECT confidence_score FROM " + testTable + " WHERE id = ?", Double.class, row2)).isEqualTo(0.0);
        assertThat(jdbcTemplate.queryForObject("SELECT confidence_score FROM " + testTable + " WHERE id = ?", Double.class, row3)).isEqualTo(1.0);
        assertThat(jdbcTemplate.queryForObject("SELECT confidence_score FROM " + testTable + " WHERE id = ?", Double.class, row4)).isNull();

        // Step 5: Verify out-of-bounds rejection (< 0.0 and > 1.0)
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 'v1', -0.0001)", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO " + testTable + " VALUES (?, ?, 'v1', 1.0001)", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup
        jdbcTemplate.execute("DROP TABLE " + testTable);
    }
}
