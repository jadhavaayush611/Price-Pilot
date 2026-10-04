package com.pricepilot.product;

import com.pricepilot.config.DatabaseSeeder;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ProductCatalogImageVerificationTest {

    private static final List<String> DISQUALIFIED_URL_PATTERNS = List.of(
            "Drama_actress",
            "gameboy",
            "Shibuya_Stream",
            "Google_pixel_8a",
            "booth",
            "teardown",
            "cardboard",
            "shipping_box",
            "retail_box",
            "mock",
            "boot_screen",
            "logo",
            "flag",
            "diagram",
            "airport"
    );

    @Test
    @DisplayName("Catalog seed images must adhere to strict visual and URL correctness criteria")
    void verifyCatalogSeedImages() throws Exception {
        InputStream is = DatabaseSeeder.class.getResourceAsStream("/com/pricepilot/config/DatabaseSeeder.class");
        assertNotNull(is, "DatabaseSeeder class must be available");

        Map<String, String> seededImages = extractSeedImagesFromSource();
        assertFalse(seededImages.isEmpty(), "Expected to extract seeded product definitions");

        for (Map.Entry<String, String> entry : seededImages.entrySet()) {
            String product = entry.getKey();
            String imageUrl = entry.getValue();

            if (imageUrl != null) {
                // Must use HTTPS
                assertTrue(imageUrl.startsWith("https://"),
                        "Image URL for product '" + product + "' must start with https://: " + imageUrl);

                // Must not be base64 or placeholder domain
                assertFalse(imageUrl.contains("via.placeholder.com"),
                        "Image URL for product '" + product + "' must not use placeholder domain");
                assertFalse(imageUrl.startsWith("data:"),
                        "Image URL for product '" + product + "' must not be data URL");

                // Must end with valid image extension
                String cleanUrl = imageUrl.split("\\?")[0].toLowerCase();
                assertTrue(cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") || cleanUrl.endsWith(".png") || cleanUrl.endsWith(".webp"),
                        "Image URL for product '" + product + "' must have standard image extension: " + imageUrl);

                // Regression check against known false-positive tokens
                for (String disqualified : DISQUALIFIED_URL_PATTERNS) {
                    assertFalse(imageUrl.toLowerCase().contains(disqualified.toLowerCase()),
                            "Image URL for product '" + product + "' contains disqualified substring '" + disqualified + "': " + imageUrl);
                }
            }
        }
    }

    @Test
    @DisplayName("Regression: Known false-positive products must have null imageUrl")
    void verifyKnownFalsePositivesAreRejected() throws Exception {
        Map<String, String> seededImages = extractSeedImagesFromSource();

        // Google Pixel 8a was previously matched to a temple photo
        assertNull(seededImages.get("Google Pixel 8a"),
                "Google Pixel 8a must be null until an unquestionably verified product photo is available");

        // Pixel Watch 2 was previously matched to Shibuya Stream building
        assertNull(seededImages.get("Pixel Watch 2"),
                "Pixel Watch 2 must be null until an unquestionably verified product photo is available");

        // Google Pixel Tablet was previously matched to Shibuya Stream building
        assertNull(seededImages.get("Google Pixel Tablet"),
                "Google Pixel Tablet must be null until an unquestionably verified product photo is available");

        // DJI Osmo Pocket 3 was previously matched to a drama actress filming
        assertNull(seededImages.get("DJI Osmo Pocket 3"),
                "DJI Osmo Pocket 3 must be null until an unquestionably verified product photo is available");

        // Samsung Galaxy Z Flip 5 was previously matched to a Game Boy comparison photo
        assertNull(seededImages.get("Samsung Galaxy Z Flip 5"),
                "Samsung Galaxy Z Flip 5 must be null until an unquestionably verified product photo is available");
    }

    @Test
    @DisplayName("ProductEntity imageUrl maps cleanly to ProductResponseDTO")
    void testProductEntityImageUrlMapping() {
        ProductEntity entity = ProductEntity.builder()
                .name("iPhone 15 Pro")
                .brand("Apple")
                .category("Smartphone")
                .description("Flagship Apple smartphone")
                .imageUrl("https://upload.wikimedia.org/wikipedia/commons/c/c3/Apple_iPhone_15_Pro.jpg")
                .archived(false)
                .build();
        entity.setId(UUID.randomUUID());

        ProductResponseDTO dto = ProductResponseDTO.fromEntity(entity);
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/c/c3/Apple_iPhone_15_Pro.jpg", dto.getImageUrl());
        assertEquals("iPhone 15 Pro", dto.getName());
    }

    private Map<String, String> extractSeedImagesFromSource() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();

        java.nio.file.Path seederPath = java.nio.file.Paths.get("src/main/java/com/pricepilot/config/DatabaseSeeder.java");
        if (!java.nio.file.Files.exists(seederPath)) {
            seederPath = java.nio.file.Paths.get("backend/src/main/java/com/pricepilot/config/DatabaseSeeder.java");
        }

        if (java.nio.file.Files.exists(seederPath)) {
            String content = java.nio.file.Files.readString(seederPath, StandardCharsets.UTF_8);
            Pattern pWithImg = Pattern.compile("productInfos\\.add\\(new ProductInfo\\(\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*([0-9.]+),\\s*\"([^\"]+)\"\\)\\);");
            Pattern pWithoutImg = Pattern.compile("productInfos\\.add\\(new ProductInfo\\(\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*([0-9.]+)\\)\\);");

            Matcher m1 = pWithImg.matcher(content);
            while (m1.find()) {
                map.put(m1.group(1), m1.group(6));
            }

            Matcher m2 = pWithoutImg.matcher(content);
            while (m2.find()) {
                if (!map.containsKey(m2.group(1))) {
                    map.put(m2.group(1), null);
                }
            }
        }

        return map;
    }
}
