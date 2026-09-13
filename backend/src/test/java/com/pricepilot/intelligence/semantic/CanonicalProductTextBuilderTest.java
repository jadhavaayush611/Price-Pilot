package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.contract.CanonicalProductText;
import com.pricepilot.intelligence.semantic.contract.CanonicalProductTextBuilder;
import com.pricepilot.product.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CanonicalProductTextBuilderTest {

    private CanonicalProductTextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new CanonicalProductTextBuilder(2048);
    }

    @Test
    @DisplayName("Should produce exact deterministic canonical string with correct field ordering")
    void testDeterministicFieldOrdering() {
        ProductEntity product = ProductEntity.builder()
                .name("iPhone 15 Pro Max")
                .brand("Apple")
                .category("Smartphones")
                .description("Flagship smartphone with titanium frame and A17 Pro chip.")
                .build();
        product.setId(UUID.randomUUID());

        CanonicalProductText canonical = builder.build(product);

        String expected = "title: iPhone 15 Pro Max | brand: Apple | category: Smartphones | description: Flagship smartphone with titanium frame and A17 Pro chip.";
        assertThat(canonical.getCanonicalText()).isEqualTo(expected);
        assertThat(canonical.getVersion()).isEqualTo(CanonicalProductTextBuilder.CANONICAL_VERSION);
        assertThat(canonical.getSemanticHash()).isNotBlank();
        assertThat(canonical.getAttributes()).containsEntry("brand", "Apple");
        assertThat(canonical.getAttributes()).containsEntry("category", "Smartphones");
        assertThat(canonical.getAttributes()).containsEntry("canonicalVersion", "product-semantic-v1");
    }

    @Test
    @DisplayName("Should omit optional brand and description when null or blank")
    void testOmitOptionalFields() {
        ProductEntity productNoBrand = ProductEntity.builder()
                .name("Generic USB-C Cable")
                .brand(null)
                .category("Accessories")
                .description("1 meter charging cable")
                .build();

        CanonicalProductText c1 = builder.build(productNoBrand);
        assertThat(c1.getCanonicalText()).isEqualTo("title: Generic USB-C Cable | category: Accessories | description: 1 meter charging cable");
        assertThat(c1.getAttributes()).doesNotContainKey("brand");

        ProductEntity productNoDesc = ProductEntity.builder()
                .name("Sony WH-1000XM5")
                .brand("Sony")
                .category("Audio")
                .description("   ")
                .build();

        CanonicalProductText c2 = builder.build(productNoDesc);
        assertThat(c2.getCanonicalText()).isEqualTo("title: Sony WH-1000XM5 | brand: Sony | category: Audio");

        ProductEntity minimal = ProductEntity.builder()
                .name("Basic Tee")
                .brand(null)
                .category("Clothing")
                .description(null)
                .build();

        CanonicalProductText c3 = builder.build(minimal);
        assertThat(c3.getCanonicalText()).isEqualTo("title: Basic Tee | category: Clothing");
    }

    @Test
    @DisplayName("Should normalize whitespace, tabs, newlines, and Unicode NFC canonical composition")
    void testWhitespaceAndUnicodeNormalization() {
        String name = "  Apple \t\n  MacBook   Pro  16\"  ";
        String brand = " Apple \r\n ";
        String category = "  Laptops  ";
        String description = "M3 \u0041\u030A Max \t\t chip   with  36GB unified memory.\n\nPowerful."; // \u0041\u030A is decomposed Å

        CanonicalProductText canonical = builder.build(name, brand, category, description);

        assertThat(canonical.getCanonicalText()).isEqualTo(
                "title: Apple MacBook Pro 16\" | brand: Apple | category: Laptops | description: M3 \u00C5 Max chip with 36GB unified memory. Powerful."
        );
    }

    @Test
    @DisplayName("Should truncate oversized description to configured maxDescriptionLength")
    void testDescriptionTruncation() {
        CanonicalProductTextBuilder shortBuilder = new CanonicalProductTextBuilder(20);
        String longDesc = "This is a very long description that exceeds twenty characters easily.";

        CanonicalProductText canonical = shortBuilder.build("Phone", "BrandX", "Electronics", longDesc);
        assertThat(canonical.getCanonicalText()).isEqualTo("title: Phone | brand: BrandX | category: Electronics | description: This is a very long");
    }

    @Test
    @DisplayName("Should reject null or blank required fields (name and category)")
    void testRequiredFieldValidation() {
        assertThatThrownBy(() -> builder.build(null, "Apple", "Smartphones", "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");

        assertThatThrownBy(() -> builder.build("   ", "Apple", "Smartphones", "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be empty");

        assertThatThrownBy(() -> builder.build("iPhone", "Apple", null, "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category is required");

        assertThatThrownBy(() -> builder.build("iPhone", "Apple", "   ", "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category cannot be empty");
    }

    @Test
    @DisplayName("Should detect semantic changes and ignore non-semantic changes")
    void testSemanticDifferenceDetection() {
        ProductEntity base = ProductEntity.builder()
                .name("Samsung Galaxy S24 Ultra")
                .brand("Samsung")
                .category("Smartphones")
                .description("256GB Titanium Gray")
                .build();

        ProductEntity sameSemantic = ProductEntity.builder()
                .name("Samsung Galaxy S24 Ultra")
                .brand("Samsung")
                .category("Smartphones")
                .description("256GB Titanium Gray")
                .imageUrl("https://images.example.com/new-image.jpg") // Volatile
                .build();

        assertThat(builder.hasSemanticDifference(base, sameSemantic)).isFalse();

        ProductEntity modifiedName = ProductEntity.builder()
                .name("Samsung Galaxy S24 Ultra 512GB")
                .brand("Samsung")
                .category("Smartphones")
                .description("256GB Titanium Gray")
                .build();

        assertThat(builder.hasSemanticDifference(base, modifiedName)).isTrue();

        ProductEntity modifiedBrand = ProductEntity.builder()
                .name("Samsung Galaxy S24 Ultra")
                .brand("Samsung Electronics")
                .category("Smartphones")
                .description("256GB Titanium Gray")
                .build();

        assertThat(builder.hasSemanticDifference(base, modifiedBrand)).isTrue();
    }
}
