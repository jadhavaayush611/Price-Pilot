package com.pricepilot.intelligence.discovery.intent;

import com.pricepilot.currency.CurrencyCode;
import com.pricepilot.currency.CurrencyConversionServiceImpl;
import com.pricepilot.currency.ConfiguredCurrencyRateProvider;
import com.pricepilot.currency.CurrencyProperties;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicShoppingQueryInterpreterTest {

    private DeterministicShoppingQueryInterpreter interpreter;

    @BeforeEach
    void setUp() {
        QueryNormalizer normalizer = new QueryNormalizer();
        CurrencyProperties properties = new CurrencyProperties();
        ConfiguredCurrencyRateProvider rateProvider = new ConfiguredCurrencyRateProvider(properties);
        CurrencyConversionServiceImpl conversionService = new CurrencyConversionServiceImpl(rateProvider, properties);
        interpreter = new DeterministicShoppingQueryInterpreter(normalizer, conversionService);
    }

    // ==========================================
    // SECTION A: Price & Currency Parsing
    // ==========================================

    @Test
    @DisplayName("Price Parsing: Should parse 'under ₹10,000' and convert to canonical USD")
    void testParseUnderRupeesWithCommas() {
        ShoppingQueryIntent intent = interpreter.interpret("wireless headphones under ₹10,000");

        // 10,000 INR / 80.0 = 125.00 USD canonical
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.INR);
        assertThat(intent.getCategory()).isEqualTo("Headphones");
        assertThat(intent.getSemanticQuery()).isEqualTo("wireless headphones");
        assertThat(intent.isHasConflicts()).isFalse();
    }

    @Test
    @DisplayName("Price Parsing: Should parse 'below 10000' with default user currency fallback (INR)")
    void testParseBelowPlainNumber() {
        ShoppingQueryIntent intent = interpreter.interpret("laptop below 10000");

        // Bare number defaults to INR -> 10000 / 80 = 125.00 USD
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.INR);
        assertThat(intent.getCategory()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Price Parsing: Should parse 'under $1000' with explicit USD")
    void testParseExplicitUsd() {
        ShoppingQueryIntent intent = interpreter.interpret("laptop under $1000");

        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.USD);
        assertThat(intent.getCategory()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Price Parsing: Should parse 'between ₹5,000 and ₹10,000'")
    void testParsePriceRangeBetween() {
        ShoppingQueryIntent intent = interpreter.interpret("headphones between ₹5,000 and ₹10,000");

        // 5000 INR = 62.50 USD, 10000 INR = 125.00 USD
        assertThat(intent.getMinPrice()).isEqualByComparingTo(new BigDecimal("62.50"));
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(intent.getRawMinPrice()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.INR);
        assertThat(intent.getCategory()).isEqualTo("Headphones");
        assertThat(intent.isHasConflicts()).isFalse();
    }

    @Test
    @DisplayName("Price Parsing: Should parse currency shorthand 'under 10k' and 'under 90k'")
    void testParseKiloShorthand() {
        ShoppingQueryIntent intent1 = interpreter.interpret("headphones under 10k");
        assertThat(intent1.getMaxPrice()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(intent1.getRawMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        ShoppingQueryIntent intent2 = interpreter.interpret("gaming laptop under 90k");
        assertThat(intent2.getMaxPrice()).isEqualByComparingTo(new BigDecimal("1125.00"));
        assertThat(intent2.getRawMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(90000));
        assertThat(intent2.getCategory()).isEqualTo("Laptop");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "smartphone under Rs 8000",
            "smartphone under Rs. 8,000",
            "smartphone under 8000 INR",
            "smartphone under ₹8000"
    })
    @DisplayName("Price Parsing: Should handle diverse INR currency formats consistently (8000 INR -> 100 USD)")
    void testDiverseInrCurrencyFormats(String query) {
        ShoppingQueryIntent intent = interpreter.interpret(query);
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(8000));
        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.INR);
        assertThat(intent.getCategory()).isEqualTo("Smartphone");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "smartphone under $100",
            "smartphone under 100 USD"
    })
    @DisplayName("Price Parsing: Should handle USD currency formats consistently")
    void testDiverseUsdCurrencyFormats(String query) {
        ShoppingQueryIntent intent = interpreter.interpret(query);
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.USD);
        assertThat(intent.getCategory()).isEqualTo("Smartphone");
    }

    @Test
    @DisplayName("Price Parsing: Should parse minimum price 'above ₹1,600'")
    void testParseMinPriceDecimal() {
        ShoppingQueryIntent intent = interpreter.interpret("smartphones above ₹1,600");

        // 1600 INR / 80 = 20.00 USD
        assertThat(intent.getMinPrice()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(intent.getRawMinPrice()).isEqualByComparingTo(new BigDecimal("1600"));
        assertThat(intent.getCategory()).isEqualTo("Smartphone");
    }

    // ==========================================
    // SECTION B: Rating Parsing
    // ==========================================

    @Test
    @DisplayName("Rating Parsing: Should parse '4 star and above'")
    void testParseRatingStarAndAbove() {
        ShoppingQueryIntent intent = interpreter.interpret("headphones 4 star and above");

        assertThat(intent.getMinRating()).isEqualTo(4.0);
        assertThat(intent.getCategory()).isEqualTo("Headphones");
    }

    @Test
    @DisplayName("Rating Parsing: Should parse 'at least 4.5 stars'")
    void testParseRatingAtLeastDecimal() {
        ShoppingQueryIntent intent = interpreter.interpret("laptop at least 4.5 stars");

        assertThat(intent.getMinRating()).isEqualTo(4.5);
        assertThat(intent.getCategory()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Rating Parsing: Should parse '4+ stars'")
    void testParseRatingPlusStars() {
        ShoppingQueryIntent intent = interpreter.interpret("Sony headphones 4+ stars");

        assertThat(intent.getMinRating()).isEqualTo(4.0);
        assertThat(intent.getBrand()).isEqualTo("Sony");
        assertThat(intent.getCategory()).isEqualTo("Headphones");
    }

    // ==========================================
    // SECTION C: Discount Parsing
    // ==========================================

    @Test
    @DisplayName("Discount Parsing: Should parse '20% discount' and 'at least 15% off'")
    void testParseDiscount() {
        ShoppingQueryIntent intent1 = interpreter.interpret("headphones with 20% discount");
        assertThat(intent1.getMinDiscount()).isEqualByComparingTo(BigDecimal.valueOf(20));

        ShoppingQueryIntent intent2 = interpreter.interpret("laptop at least 15% off");
        assertThat(intent2.getMinDiscount()).isEqualByComparingTo(BigDecimal.valueOf(15));
    }

    // ==========================================
    // SECTION D: Availability & Brand & Category
    // ==========================================

    @Test
    @DisplayName("Availability: Should detect 'in stock' and 'available'")
    void testParseAvailability() {
        ShoppingQueryIntent intent1 = interpreter.interpret("Sony headphones in stock");
        assertThat(intent1.getInStock()).isTrue();
        assertThat(intent1.getBrand()).isEqualTo("Sony");

        ShoppingQueryIntent intent2 = interpreter.interpret("Dell laptop available");
        assertThat(intent2.getInStock()).isTrue();
        assertThat(intent2.getBrand()).isEqualTo("Dell");
    }

    @Test
    @DisplayName("Category Recognition: Should resolve aliases like 'smartphones', 'macbook', 'airpods'")
    void testCategoryAliases() {
        assertThat(interpreter.interpret("smartphones under 20000").getCategory()).isEqualTo("Smartphone");
        assertThat(interpreter.interpret("macbook under 80000").getCategory()).isEqualTo("Laptop");
        assertThat(interpreter.interpret("airpods in stock").getCategory()).isEqualTo("Headphones");
        assertThat(interpreter.interpret("nintendo switch games").getCategory()).isEqualTo("Gaming");
    }

    // ==========================================
    // SECTION E: Semantic Query Extraction
    // ==========================================

    @Test
    @DisplayName("Semantic Extraction: 'wireless headphones under ₹10,000 with good ANC and at least 4 stars'")
    void testComplexShoppingQueryExtraction() {
        String query = "wireless headphones under ₹10,000 with good ANC and at least 4 stars";
        ShoppingQueryIntent intent = interpreter.interpret(query);

        assertThat(intent.getCategory()).isEqualTo("Headphones");
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(intent.getMinRating()).isEqualTo(4.0);
        assertThat(intent.getSemanticQuery()).contains("wireless headphones").contains("good ANC");
        assertThat(intent.getSemanticQuery()).doesNotContain("10000").doesNotContain("4 stars");
    }

    @Test
    @DisplayName("Semantic Extraction: 'best lightweight laptop under ₹90,000 with great battery life'")
    void testSemanticLaptopExtraction() {
        String query = "best lightweight laptop under ₹90,000 with great battery life";
        ShoppingQueryIntent intent = interpreter.interpret(query);

        assertThat(intent.getCategory()).isEqualTo("Laptop");
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("1125.00"));
        assertThat(intent.getSemanticQuery()).contains("lightweight laptop").contains("great battery life");
        assertThat(intent.getSemanticQuery()).doesNotContain("90000");
    }

    // ==========================================
    // SECTION F: Conflicts & Contradictions
    // ==========================================

    @Test
    @DisplayName("Conflict Detection: Should flag contradictory price range 'between ₹20,000 and ₹10,000'")
    void testConflictingPriceRange() {
        ShoppingQueryIntent intent = interpreter.interpret("laptop between ₹20,000 and ₹10,000");

        assertThat(intent.isHasConflicts()).isTrue();
        assertThat(intent.getConflictDescription()).contains("exceeds");
    }

    @Test
    @DisplayName("Conflict Detection: Should flag 'under ₹10,000 but above ₹20,000'")
    void testConflictingMinAndMaxPrices() {
        ShoppingQueryIntent intent = interpreter.interpret("laptop under ₹10,000 and above ₹20,000");

        assertThat(intent.isHasConflicts()).isTrue();
        assertThat(intent.getConflictDescription()).contains("exceeds");
    }

    // ==========================================
    // SECTION G: Determinism & Safety
    // ==========================================

    @Test
    @DisplayName("Determinism: Repeated queries must produce exact identical ShoppingQueryIntent")
    void testDeterministicReproducibility() {
        String query = "Bose wireless earbuds under $250 with at least 4 stars in stock";
        ShoppingQueryIntent first = interpreter.interpret(query);
        ShoppingQueryIntent second = interpreter.interpret(query);

        assertThat(first.getRawQuery()).isEqualTo(second.getRawQuery());
        assertThat(first.getSemanticQuery()).isEqualTo(second.getSemanticQuery());
        assertThat(first.getCategory()).isEqualTo(second.getCategory());
        assertThat(first.getBrand()).isEqualTo(second.getBrand());
        assertThat(first.getMaxPrice()).isEqualTo(second.getMaxPrice());
        assertThat(first.getMinRating()).isEqualTo(second.getMinRating());
        assertThat(first.getInStock()).isEqualTo(second.getInStock());
    }

    @Test
    @DisplayName("Safety: Pathological length query must be bounded without error")
    void testPathologicalInputSafety() {
        String longQuery = "headphones " + "very good ".repeat(500) + "under 5000";
        ShoppingQueryIntent intent = interpreter.interpret(longQuery);

        assertThat(intent).isNotNull();
        // 5000 INR / 80 = 62.50 USD
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("62.50"));
        assertThat(intent.getCategory()).isEqualTo("Headphones");
    }
}
