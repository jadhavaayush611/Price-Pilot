package com.pricepilot.intelligence.discovery;

import com.pricepilot.currency.CurrencyCode;
import com.pricepilot.currency.CurrencyConversionServiceImpl;
import com.pricepilot.currency.ConfiguredCurrencyRateProvider;
import com.pricepilot.currency.CurrencyProperties;
import com.pricepilot.intelligence.discovery.intent.DeterministicShoppingQueryInterpreter;
import com.pricepilot.intelligence.discovery.intent.ShoppingQueryIntent;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("End-to-End Currency Natural-Language Shopping Query Resolution")
class ShoppingQueryCurrencyIntegrationTest {

    private DeterministicShoppingQueryInterpreter interpreter;

    @BeforeEach
    void setUp() {
        CurrencyProperties properties = new CurrencyProperties();
        ConfiguredCurrencyRateProvider rateProvider = new ConfiguredCurrencyRateProvider(properties);
        CurrencyConversionServiceImpl conversionService = new CurrencyConversionServiceImpl(rateProvider, properties);
        interpreter = new DeterministicShoppingQueryInterpreter(new QueryNormalizer(), conversionService);
    }

    @Test
    @DisplayName("Defect reproduction test: 'wireless headphones under ₹5000' converts ₹5000 INR to $62.50 USD canonical")
    void testDefectFixWirelessHeadphonesUnder5000Inr() {
        // Given: The defect query reported in v1.2 runtime acceptance test
        String query = "wireless headphones under ₹5000";

        // When: Interpreted
        ShoppingQueryIntent intent = interpreter.interpret(query);

        // Then:
        assertThat(intent.getCategory()).isEqualTo("Headphones");
        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.INR);
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(new BigDecimal("5000"));
        // ₹5000 / 80.0 = $62.50 USD canonical
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("62.50"));
        assertThat(intent.getSemanticQuery()).isEqualTo("wireless headphones");
        assertThat(intent.isHasConflicts()).isFalse();
    }

    @Test
    @DisplayName("Explicit USD query: 'gaming laptop under $1500' preserves USD 1500 canonical")
    void testExplicitUsdQuery() {
        String query = "gaming laptop under $1500";
        ShoppingQueryIntent intent = interpreter.interpret(query);

        assertThat(intent.getCategory()).isEqualTo("Laptop");
        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.USD);
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    @DisplayName("Explicit EUR query: 'mechanical keyboard under €90' converts €90 to $100 USD canonical")
    void testExplicitEurQuery() {
        String query = "mechanical keyboard under €90";
        ShoppingQueryIntent intent = interpreter.interpret(query);

        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.EUR);
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(new BigDecimal("90"));
        // €90 / 0.90 = $100.00 USD
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Explicit GBP query: 'headphones under £80' converts £80 to $100 USD canonical")
    void testExplicitGbpQuery() {
        String query = "headphones under £80";
        ShoppingQueryIntent intent = interpreter.interpret(query);

        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.GBP);
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(new BigDecimal("80"));
        // £80 / 0.80 = $100.00 USD
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Explicit JPY query: 'retro games under ¥15000' converts ¥15000 to $100 USD canonical")
    void testExplicitJpyQuery() {
        String query = "retro games under ¥15000";
        ShoppingQueryIntent intent = interpreter.interpret(query);

        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.JPY);
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(new BigDecimal("15000"));
        // ¥15000 / 150.0 = $100.00 USD
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Multipliers with currency: 'laptop under 1.5 lakh inr' converts 150000 INR to $1875 USD canonical")
    void testMultiplierWithCurrency() {
        String query = "laptop under 1.5 lakh inr";
        ShoppingQueryIntent intent = interpreter.interpret(query);

        assertThat(intent.getCategory()).isEqualTo("Laptop");
        assertThat(intent.getSourceCurrency()).isEqualTo(CurrencyCode.INR);
        assertThat(intent.getRawMaxPrice()).isEqualByComparingTo(new BigDecimal("150000"));
        // 150,000 / 80 = 1875.00 USD
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(new BigDecimal("1875.00"));
    }
}
