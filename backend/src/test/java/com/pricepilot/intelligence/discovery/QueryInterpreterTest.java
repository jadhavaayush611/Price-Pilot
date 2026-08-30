package com.pricepilot.intelligence.discovery;

import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.intelligence.discovery.interpretation.QueryInterpreter;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class QueryInterpreterTest {

    private QueryInterpreter interpreter;

    @BeforeEach
    void setUp() {
        interpreter = new QueryInterpreter(new QueryNormalizer());
    }

    @Test
    @DisplayName("Extracts max price from 'iphone under 70000'")
    void testIphoneUnder70000() {
        InterpretedQuery result = interpreter.interpret("iphone under 70000");

        assertNotNull(result);
        assertEquals("Smartphone", result.getDetectedCategory());
        assertEquals(new BigDecimal("70000"), result.getMaxPrice());
        assertNull(result.getMinPrice());
        assertTrue(result.getSearchTokens().contains("iphone"));
    }

    @Test
    @DisplayName("Extracts brand, category, and deal intent from 'best samsung phone'")
    void testBestSamsungPhone() {
        InterpretedQuery result = interpreter.interpret("best samsung phone");

        assertNotNull(result);
        assertEquals("Samsung", result.getDetectedBrand());
        assertEquals("Smartphone", result.getDetectedCategory());
        assertTrue(Boolean.TRUE.equals(result.getDealIntent()));
        assertTrue(result.getSearchTokens().contains("phone") || result.getSearchTokens().contains("samsung"));
    }

    @Test
    @DisplayName("Extracts category, max price, and specifications from 'laptop under 80000 with 16gb'")
    void testLaptopUnder80000With16GB() {
        InterpretedQuery result = interpreter.interpret("laptop under 80000 with 16gb");

        assertNotNull(result);
        assertEquals("Laptop", result.getDetectedCategory());
        assertEquals(new BigDecimal("80000"), result.getMaxPrice());
        assertTrue(result.getSearchTokens().contains("16gb"));
    }

    @Test
    @DisplayName("Extracts price range from 'between 500 and 1200'")
    void testPriceBetweenRange() {
        InterpretedQuery result = interpreter.interpret("sony headphones between 500 and 1200");

        assertNotNull(result);
        assertEquals("Sony", result.getDetectedBrand());
        assertEquals("Headphones", result.getDetectedCategory());
        assertEquals(new BigDecimal("500"), result.getMinPrice());
        assertEquals(new BigDecimal("1200"), result.getMaxPrice());
    }

    @Test
    @DisplayName("Extracts rating constraint from 'above 4.5 stars'")
    void testRatingConstraint() {
        InterpretedQuery result = interpreter.interpret("gaming console above 4.5 stars");

        assertNotNull(result);
        assertEquals("Gaming", result.getDetectedCategory());
        assertEquals(4.5, result.getMinRating());
    }

    @Test
    @DisplayName("Extracts availability constraint from 'in stock'")
    void testInStockConstraint() {
        InterpretedQuery result = interpreter.interpret("apple macbook in stock");

        assertNotNull(result);
        assertEquals("Apple", result.getDetectedBrand());
        assertEquals("Laptop", result.getDetectedCategory());
        assertTrue(Boolean.TRUE.equals(result.getInStockOnly()));
    }

    @Test
    @DisplayName("Handles unconstrained query without fabricating data")
    void testUnconstrainedQuery() {
        InterpretedQuery result = interpreter.interpret("running shoes");

        assertNotNull(result);
        assertNull(result.getDetectedBrand());
        assertNull(result.getDetectedCategory());
        assertNull(result.getMinPrice());
        assertNull(result.getMaxPrice());
        assertNull(result.getMinRating());
        assertFalse(Boolean.TRUE.equals(result.getDealIntent()));
        assertTrue(result.getSearchTokens().contains("running"));
        assertTrue(result.getSearchTokens().contains("shoes"));
    }
}
