package com.pricepilot.intelligence.personalization.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PersonalizationContextProvider Contract Tests")
class PersonalizationContextProviderTest {

    @Test
    @DisplayName("Provider functional contract operates without persistence or HTTP dependencies")
    void testProviderContractLambda() {
        UUID userId = UUID.randomUUID();

        // Functional lambda mock implementation without any repository or database coupling
        PersonalizationContextProvider provider = id -> PersonalizationContext.builder(id)
                .addPreferredCategory("smartphones")
                .addPreferredBrand("apple")
                .build();

        PersonalizationContext context = provider.getPersonalizationContext(userId);

        assertNotNull(context);
        assertEquals(userId, context.getUserId());
        assertTrue(context.getPreferredCategories().contains("smartphones"));
        assertTrue(context.getPreferredBrands().contains("apple"));
    }
}
