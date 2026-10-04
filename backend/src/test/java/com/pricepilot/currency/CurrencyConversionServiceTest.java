package com.pricepilot.currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Currency Conversion & Monetary Intelligence Unit Tests")
class CurrencyConversionServiceTest {

    private CurrencyConversionService conversionService;
    private CurrencyRateProvider rateProvider;
    private CurrencyProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CurrencyProperties();
        rateProvider = new ConfiguredCurrencyRateProvider(properties);
        conversionService = new CurrencyConversionServiceImpl(rateProvider, properties);
    }

    @Test
    @DisplayName("Canonical currency must be USD and default display currency must be INR")
    void testDefaults() {
        assertThat(conversionService.getCanonicalCurrency()).isEqualTo(CurrencyCode.USD);
        assertThat(conversionService.getDefaultDisplayCurrency()).isEqualTo(CurrencyCode.INR);
    }

    @ParameterizedTest(name = "{0} {1} -> {2} USD")
    @CsvSource({
            "80.00, INR, 1.00",
            "5000.00, INR, 62.50",
            "10000.00, INR, 125.00",
            "1.00, USD, 1.00",
            "100.00, USD, 100.00",
            "90.00, EUR, 100.00",
            "80.00, GBP, 100.00",
            "15000.00, JPY, 100.00"
    })
    @DisplayName("Convert to canonical USD")
    void testConvertToCanonical(String amount, String currencyCode, String expectedUsd) {
        CurrencyCode code = CurrencyCode.valueOf(currencyCode);
        BigDecimal converted = conversionService.convertToCanonical(new BigDecimal(amount), code);
        assertThat(converted).isEqualByComparingTo(new BigDecimal(expectedUsd));
    }

    @ParameterizedTest(name = "{0} USD -> {2} {1}")
    @CsvSource({
            "1.00, INR, 80.00",
            "62.50, INR, 5000.00",
            "125.00, INR, 10000.00",
            "100.00, EUR, 90.00",
            "100.00, GBP, 80.00",
            "100.00, JPY, 15000",
            "604.975, USD, 604.98",
            "604.975, INR, 48398.00",
            "604.975, EUR, 544.48",
            "604.975, GBP, 483.98",
            "604.975, JPY, 90746"
    })
    @DisplayName("Convert from canonical USD to target display currency")
    void testConvertFromCanonical(String usdAmount, String targetCode, String expectedTarget) {
        CurrencyCode code = CurrencyCode.valueOf(targetCode);
        BigDecimal converted = conversionService.convertFromCanonical(new BigDecimal(usdAmount), code);
        assertThat(converted).isEqualByComparingTo(new BigDecimal(expectedTarget));
    }

    @Test
    @DisplayName("Null amounts must return null safely without NullPointerException")
    void testNullSafe() {
        assertThat(conversionService.convertToCanonical(null, CurrencyCode.INR)).isNull();
        assertThat(conversionService.convertFromCanonical(null, CurrencyCode.INR)).isNull();
        assertThat(conversionService.convert((BigDecimal) null, CurrencyCode.INR, CurrencyCode.USD)).isNull();
        assertThat(conversionService.convert((Money) null, CurrencyCode.USD)).isNull();
    }

    @Test
    @DisplayName("Money value object comparisons and arithmetic")
    void testMoneyValueObject() {
        Money m1 = Money.of(new BigDecimal("100.00"), CurrencyCode.USD);
        Money m2 = Money.usd(150.00);
        Money m3 = Money.inr(8000.00);

        assertThat(m1.isPositive()).isTrue();
        assertThat(m1.compareTo(m2)).isNegative();
        assertThat(m2.compareTo(m1)).isPositive();

        // Cross currency Money comparison throws exception
        assertThatThrownBy(() -> m1.compareTo(m3))
                .isInstanceOf(IllegalArgumentException.class);

        // Convert Money
        Money convertedToInr = conversionService.convert(m1, CurrencyCode.INR);
        assertThat(convertedToInr.getCurrency()).isEqualTo(CurrencyCode.INR);
        assertThat(convertedToInr.getAmount()).isEqualByComparingTo(new BigDecimal("8000.00"));
    }
}
