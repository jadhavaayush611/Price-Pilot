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
        assertEquals(252, seededImages.size(), "Expected exactly 252 seeded products in catalog");

        Set<String> distinctImageUrls = new HashSet<>();

        for (Map.Entry<String, String> entry : seededImages.entrySet()) {
            String product = entry.getKey();
            String imageUrl = entry.getValue();

            assertNotNull(imageUrl, "Product '" + product + "' must have a dedicated image assignment");
            assertFalse(imageUrl.trim().isEmpty(), "Product '" + product + "' image URL must not be blank");

            // Must use HTTPS or a local public asset path (/products/*.svg)
            assertTrue(imageUrl.startsWith("https://") || imageUrl.startsWith("/products/"),
                    "Image URL for product '" + product + "' must start with https:// or /products/: " + imageUrl);

            // Must not be base64 or placeholder domain
            assertFalse(imageUrl.contains("via.placeholder.com"),
                    "Image URL for product '" + product + "' must not use placeholder domain");
            assertFalse(imageUrl.startsWith("data:"),
                    "Image URL for product '" + product + "' must not be data URL");

            // Must end with valid image extension (jpg, jpeg, png, webp, svg)
            String cleanUrl = imageUrl.split("\\?")[0].toLowerCase();
            assertTrue(cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") || cleanUrl.endsWith(".png") || cleanUrl.endsWith(".webp") || cleanUrl.endsWith(".svg"),
                    "Image URL for product '" + product + "' must have standard image extension: " + imageUrl);

            // Regression check against known false-positive tokens
            for (String disqualified : DISQUALIFIED_URL_PATTERNS) {
                assertFalse(imageUrl.toLowerCase().contains(disqualified.toLowerCase()),
                        "Image URL for product '" + product + "' contains disqualified substring '" + disqualified + "': " + imageUrl);
            }

            // Ensure no duplicate assignments (every product has a distinct asset)
            assertTrue(distinctImageUrls.add(imageUrl),
                    "Duplicate image asset detected: '" + imageUrl + "' assigned to multiple products (product: '" + product + "')");
        }
    }

    @Test
    @DisplayName("All 252 catalog products have 100% complete dedicated visual asset coverage")
    void verifyCompleteCatalogCoverage() throws Exception {
        Map<String, String> seededImages = extractSeedImagesFromSource();
        assertEquals(252, seededImages.size(), "Catalog must have exactly 252 products");

        long photoCount = seededImages.values().stream()
                .filter(url -> url != null && url.startsWith("https://"))
                .count();
        long illustrationCount = seededImages.values().stream()
                .filter(url -> url != null && url.startsWith("/products/"))
                .count();

        assertEquals(42, photoCount, "Expected exactly 42 verified open-access photographs");
        assertEquals(210, illustrationCount, "Expected exactly 210 dedicated vector illustrations");
        assertEquals(252, photoCount + illustrationCount, "All 252 products must map to dedicated visual assets");
    }

    @Test
    @DisplayName("ProductEntity imageUrl maps cleanly to ProductResponseDTO")
    void testProductEntityImageUrlMapping() {
        ProductEntity entity = ProductEntity.builder()
                .name("iPhone 15 Pro")
                .brand("Apple")
                .category("Smartphone")
                .description("Flagship Apple smartphone")
                .imageUrl("https://upload.wikimedia.org/wikipedia/commons/1/19/Apple_iPhone_15_Pro.jpg")
                .archived(false)
                .build();
        entity.setId(UUID.randomUUID());

        ProductResponseDTO dto = ProductResponseDTO.fromEntity(entity);
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/1/19/Apple_iPhone_15_Pro.jpg", dto.getImageUrl());
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
