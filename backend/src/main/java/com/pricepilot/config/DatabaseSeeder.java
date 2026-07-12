package com.pricepilot.config;

import com.pricepilot.analytics.ProductAnalyticsEntity;
import com.pricepilot.analytics.ProductAnalyticsRepository;
import com.pricepilot.interaction.InteractionType;
import com.pricepilot.interaction.UserInteractionEventEntity;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.pricehistory.PriceHistoryEntity;
import com.pricepilot.pricehistory.PriceHistoryRepository;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;
    private final ProductPriceRepository productPriceRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final UserRepository userRepository;
    private final UserInteractionEventRepository interactionRepository;
    private final ProductAnalyticsRepository analyticsRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.core.env.Environment environment;

    public DatabaseSeeder(
            ProductRepository productRepository,
            SellerRepository sellerRepository,
            ProductPriceRepository productPriceRepository,
            PriceHistoryRepository priceHistoryRepository,
            UserRepository userRepository,
            UserInteractionEventRepository interactionRepository,
            ProductAnalyticsRepository analyticsRepository,
            PasswordEncoder passwordEncoder,
            org.springframework.core.env.Environment environment) {
        this.productRepository = productRepository;
        this.sellerRepository = sellerRepository;
        this.productPriceRepository = productPriceRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.userRepository = userRepository;
        this.interactionRepository = interactionRepository;
        this.analyticsRepository = analyticsRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Detect if running inside a JUnit test environment
        boolean isTest = false;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("org.junit.") || element.getClassName().startsWith("org.testng.")) {
                isTest = true;
                break;
            }
        }
        if (isTest || Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            log.info("Test environment detected. Skipping production seeder.");
            return;
        }

        if (productRepository.count() > 0) {
            log.info("Database already seeded. Skipping seeder.");
            return;
        }

        log.info("Starting database seeding process...");

        // 1. Seed Sellers
        List<SellerEntity> sellers = seedSellers();

        // 2. Seed Users
        List<UserEntity> users = seedUsers();

        // 3. Seed Products, Prices, and History
        seedProductsAndPrices(sellers, users);

        log.info("Database seeding completed successfully!");
    }

    private List<SellerEntity> seedSellers() {
        List<SellerEntity> list = new ArrayList<>();
        String[][] sellerData = {
            {"Amazon", "https://amazon.com", "https://upload.wikimedia.org/wikipedia/commons/a/a9/Amazon_logo.svg"},
            {"Flipkart", "https://flipkart.com", "https://upload.wikimedia.org/wikipedia/commons/7/7a/Flipkart_logo.svg"},
            {"Croma", "https://croma.com", ""},
            {"Reliance Digital", "https://reliancedigital.in", ""},
            {"Vijay Sales", "https://vijaysales.com", ""},
            {"Apple Store", "https://apple.com", "https://upload.wikimedia.org/wikipedia/commons/f/fa/Apple_logo_black.svg"},
            {"Samsung Store", "https://samsung.com", ""},
            {"Dell", "https://dell.com", ""},
            {"Lenovo", "https://lenovo.com", ""},
            {"ASUS", "https://asus.com", ""},
            {"HP", "https://hp.com", ""},
            {"Best Buy", "https://bestbuy.com", ""},
            {"Target", "https://target.com", ""},
            {"Walmart", "https://walmart.com", ""},
            {"Newegg", "https://newegg.com", ""}
        };

        for (String[] data : sellerData) {
            SellerEntity seller = SellerEntity.builder()
                    .name(data[0])
                    .websiteUrl(data[1])
                    .logoUrl(data[2].isEmpty() ? null : data[2])
                    .build();
            list.add(sellerRepository.save(seller));
        }
        return list;
    }

    private List<UserEntity> seedUsers() {
        List<UserEntity> list = new ArrayList<>();
        
        // Admin user
        UserEntity admin = UserEntity.builder()
                .email("admin@pricepilot.com")
                .password(passwordEncoder.encode("admin123"))
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .enabled(true)
                .locked(false)
                .build();
        list.add(userRepository.save(admin));

        // Test users
        for (int i = 1; i <= 5; i++) {
            UserEntity user = UserEntity.builder()
                    .email("user" + i + "@pricepilot.com")
                    .password(passwordEncoder.encode("password123"))
                    .firstName("Test")
                    .lastName("User " + i)
                    .role(Role.USER)
                    .enabled(true)
                    .locked(false)
                    .build();
            list.add(userRepository.save(user));
        }
        return list;
    }

    private void seedProductsAndPrices(List<SellerEntity> sellers, List<UserEntity> users) {
        List<ProductInfo> productInfos = new ArrayList<>();
        
        // Smartphone (18)
        productInfos.add(new ProductInfo("iPhone 15 Pro", "Apple", "Smartphone", "Flagship Apple smartphone with Titanium design and A17 Pro chip.", 999.00));
        productInfos.add(new ProductInfo("Samsung Galaxy S24 Ultra", "Samsung", "Smartphone", "Premium Samsung flagship with S-Pen, Titanium build, and AI features.", 1299.00));
        productInfos.add(new ProductInfo("Google Pixel 8 Pro", "Google", "Smartphone", "Google flagship smartphone with Tensor G3 and advanced AI photography.", 999.00));
        productInfos.add(new ProductInfo("OnePlus 12", "OnePlus", "Smartphone", "High performance phone with Snapdragon 8 Gen 3 and super fast charging.", 799.00));
        productInfos.add(new ProductInfo("Xiaomi 14 Ultra", "Xiaomi", "Smartphone", "Pro photography phone with Leica quad-camera system.", 1099.00));
        productInfos.add(new ProductInfo("iPhone 15", "Apple", "Smartphone", "Standard Apple smartphone with Dynamic Island and A16 Bionic.", 799.00));
        productInfos.add(new ProductInfo("Samsung Galaxy S24", "Samsung", "Smartphone", "Compact flagship with Galaxy AI capabilities.", 799.00));
        productInfos.add(new ProductInfo("Google Pixel 8", "Google", "Smartphone", "Compact Google phone with pure Android experience.", 699.00));
        productInfos.add(new ProductInfo("Motorola Edge 50 Ultra", "Motorola", "Smartphone", "Sleek smartphone with wooden back design and fast charging.", 899.00));
        productInfos.add(new ProductInfo("Nothing Phone 2", "Nothing", "Smartphone", "Unique phone with transparent back and glyph interface.", 599.00));
        productInfos.add(new ProductInfo("Sony Xperia 1 VI", "Sony", "Smartphone", "Professional photography and cinema-oriented smartphone.", 1199.00));
        productInfos.add(new ProductInfo("iPhone 14 Pro", "Apple", "Smartphone", "Apple flagship from previous generation with Dynamic Island.", 899.00));
        productInfos.add(new ProductInfo("Samsung Galaxy Z Fold 5", "Samsung", "Smartphone", "Premium folding phone with expansive inner display.", 1799.00));
        productInfos.add(new ProductInfo("Samsung Galaxy Z Flip 5", "Samsung", "Smartphone", "Compact pocketable folding phone with large cover screen.", 999.00));
        productInfos.add(new ProductInfo("OnePlus 12R", "OnePlus", "Smartphone", "Performance focused flagship killer with excellent battery.", 499.00));
        productInfos.add(new ProductInfo("Google Pixel 8a", "Google", "Smartphone", "Affordable Pixel phone with flagship camera features.", 499.00));
        productInfos.add(new ProductInfo("Nothing Phone 2a", "Nothing", "Smartphone", "Budget friendly transparent design smartphone.", 349.00));
        productInfos.add(new ProductInfo("Sony Xperia 5 V", "Sony", "Smartphone", "Compact premium smartphone with professional audio.", 999.00));

        // Laptop (18)
        productInfos.add(new ProductInfo("MacBook Pro 16 M3", "Apple", "Laptop", "Professional workstation with M3 Pro/Max chip and Liquid Retina XDR display.", 2499.00));
        productInfos.add(new ProductInfo("MacBook Air 13 M3", "Apple", "Laptop", "Thin, light laptop with M3 chip and long battery life.", 1099.00));
        productInfos.add(new ProductInfo("Dell XPS 15", "Dell", "Laptop", "Premium Windows laptop with InfinityEdge display and Intel Core i9.", 1899.00));
        productInfos.add(new ProductInfo("Lenovo ThinkPad X1 Carbon", "Lenovo", "Laptop", "Business laptop with carbon fiber chassis and outstanding keyboard.", 1599.00));
        productInfos.add(new ProductInfo("HP Spectre x360", "HP", "Laptop", "Convertible 2-in-1 laptop with premium gem-cut design.", 1399.00));
        productInfos.add(new ProductInfo("ASUS ROG Zephyrus G14", "ASUS", "Laptop", "Compact gaming laptop with AMD Ryzen 9 and RTX 4070.", 1599.00));
        productInfos.add(new ProductInfo("Acer Predator Helios", "Acer", "Laptop", "Heavy duty gaming laptop with high refresh rate screen.", 1299.00));
        productInfos.add(new ProductInfo("Microsoft Surface Laptop 7", "Microsoft", "Laptop", "Sleek Windows laptop with Snapdragon X Elite chip.", 999.00));
        productInfos.add(new ProductInfo("Razer Blade 16", "Razer", "Laptop", "Premium gaming laptop with dual-mode Mini-LED display.", 2999.00));
        productInfos.add(new ProductInfo("Lenovo Legion Pro 7i", "Lenovo", "Laptop", "High performance gaming laptop with RTX 4090.", 2499.00));
        productInfos.add(new ProductInfo("HP Omen 16", "HP", "Laptop", "Understated gaming laptop with great cooling.", 1199.00));
        productInfos.add(new ProductInfo("MacBook Pro 14 M3", "Apple", "Laptop", "Powerful professional laptop in a compact 14-inch chassis.", 1599.00));
        productInfos.add(new ProductInfo("Dell XPS 13 Plus", "Dell", "Laptop", "Futuristic design compact premium laptop.", 1499.00));
        productInfos.add(new ProductInfo("Lenovo Yoga Book 9i", "Lenovo", "Laptop", "Dual-screen OLED laptop for multitasking.", 1999.00));
        productInfos.add(new ProductInfo("ASUS Zenbook 14 OLED", "ASUS", "Laptop", "Sleek, lightweight laptop with stunning OLED display.", 899.00));
        productInfos.add(new ProductInfo("HP Envy x360", "HP", "Laptop", "Versatile 2-in-1 touchscreen laptop.", 799.00));
        productInfos.add(new ProductInfo("Razer Blade 14", "Razer", "Laptop", "Compact high-performance gaming laptop.", 2199.00));
        productInfos.add(new ProductInfo("Acer Swift Go 14", "Acer", "Laptop", "Thin and light laptop with Intel Core Ultra.", 749.00));

        // Tablet (18)
        productInfos.add(new ProductInfo("iPad Pro 11 M4", "Apple", "Tablet", "Ultra thin iPad with Tandem OLED display and powerful M4 chip.", 999.00));
        productInfos.add(new ProductInfo("iPad Air M2", "Apple", "Tablet", "Versatile iPad with M2 chip and Apple Pencil Pro support.", 599.00));
        productInfos.add(new ProductInfo("iPad Mini 6", "Apple", "Tablet", "Compact iPad with A15 Bionic and Apple Pencil 2 support.", 499.00));
        productInfos.add(new ProductInfo("Samsung Galaxy Tab S9 Ultra", "Samsung", "Tablet", "Massive 14.6-inch AMOLED display tablet with S-Pen included.", 1199.00));
        productInfos.add(new ProductInfo("Samsung Galaxy Tab S9 FE", "Samsung", "Tablet", "Fan edition tablet with S-Pen and water resistance.", 449.00));
        productInfos.add(new ProductInfo("Lenovo Tab P12", "Lenovo", "Tablet", "Affordable entertainment tablet with 12.7-inch 3K display.", 349.00));
        productInfos.add(new ProductInfo("OnePlus Pad", "OnePlus", "Tablet", "Fast tablet with 7:5 ratio display and Dimensity 9000.", 479.00));
        productInfos.add(new ProductInfo("Xiaomi Pad 6", "Xiaomi", "Tablet", "Budget friendly tablet with 144Hz screen and metal body.", 349.00));
        productInfos.add(new ProductInfo("Amazon Fire HD 10", "Amazon", "Tablet", "Affordable tablet for media streaming and basic tasks.", 149.00));
        productInfos.add(new ProductInfo("Google Pixel Tablet", "Google", "Tablet", "Tablet that doubles as a smart home display with charging dock.", 499.00));
        productInfos.add(new ProductInfo("Microsoft Surface Pro 11", "Microsoft", "Tablet", "2-in-1 Windows tablet with Snapdragon processor.", 999.00));
        productInfos.add(new ProductInfo("iPad Pro 13 M4", "Apple", "Tablet", "Massive Tandem OLED display flagship tablet.", 1299.00));
        productInfos.add(new ProductInfo("iPad 10th Gen", "Apple", "Tablet", "Colorful standard iPad for everyday tasks.", 349.00));
        productInfos.add(new ProductInfo("Samsung Galaxy Tab S9", "Samsung", "Tablet", "Premium water-resistant Android tablet.", 799.00));
        productInfos.add(new ProductInfo("Lenovo Tab Extreme", "Lenovo", "Tablet", "Large format entertainment tablet with dual USB-C.", 999.00));
        productInfos.add(new ProductInfo("OnePlus Pad Go", "OnePlus", "Tablet", "Affordable 2K display tablet for streaming.", 249.00));
        productInfos.add(new ProductInfo("Amazon Fire Max 11", "Amazon", "Tablet", "Premium built affordable tablet with stylus support.", 229.00));
        productInfos.add(new ProductInfo("Xiaomi Pad 6S Pro", "Xiaomi", "Tablet", "Powerful tablet with large 12.4-inch screen.", 599.00));

        // Headphones (18)
        productInfos.add(new ProductInfo("Sony WH-1000XM5", "Sony", "Headphones", "Industry leading active noise canceling over-ear headphones.", 399.00));
        productInfos.add(new ProductInfo("Bose QuietComfort Ultra", "Bose", "Headphones", "Noise canceling headphones with spatial audio technology.", 429.00));
        productInfos.add(new ProductInfo("Apple AirPods Max", "Apple", "Headphones", "Premium over-ear headphones with custom spatial audio and mesh headband.", 549.00));
        productInfos.add(new ProductInfo("Sennheiser Momentum 4", "Sennheiser", "Headphones", "Audiophile grade headphones with incredible 60-hour battery life.", 379.00));
        productInfos.add(new ProductInfo("Beats Studio Pro", "Beats", "Headphones", "Over-ear headphones with customized spatial audio and USB-C audio support.", 349.00));
        productInfos.add(new ProductInfo("Sony WH-1000XM4", "Sony", "Headphones", "Classic noise canceling headphones with foldaway design.", 299.00));
        productInfos.add(new ProductInfo("Shure AONIC 50 Gen 2", "Shure", "Headphones", "Studio quality sound with customizable ANC settings.", 349.00));
        productInfos.add(new ProductInfo("Bowers & Wilkins Px7 S2e", "Bowers & Wilkins", "Headphones", "Luxurious build quality and highly detailed acoustic performance.", 399.00));
        productInfos.add(new ProductInfo("Focal Bathys", "Focal", "Headphones", "Hi-fi active noise canceling headphones with built-in DAC mode.", 799.00));
        productInfos.add(new ProductInfo("Audio-Technica ATH-M50xBT2", "Audio-Technica", "Headphones", "Wireless studio monitor headphones with legendary audio performance.", 199.00));
        productInfos.add(new ProductInfo("Sennheiser HD 600", "Sennheiser", "Headphones", "Legendary open-back audiophile reference headphones.", 399.00));
        productInfos.add(new ProductInfo("Bose QuietComfort", "Bose", "Headphones", "Classic noise canceling comfort in a new design.", 349.00));
        productInfos.add(new ProductInfo("Sony WH-CH720N", "Sony", "Headphones", "Lightweight entry-level noise canceling headphones.", 149.00));
        productInfos.add(new ProductInfo("Sennheiser Accentum", "Sennheiser", "Headphones", "Premium sound ANC headphones with 50h battery life.", 179.00));
        productInfos.add(new ProductInfo("Beats Solo 4", "Beats", "Headphones", "On-ear headphones with ultra-long battery life.", 199.00));
        productInfos.add(new ProductInfo("Audio-Technica ATH-M55x", "Audio-Technica", "Headphones", "Professional studio tracking headphones.", 169.00));
        productInfos.add(new ProductInfo("JBL Tour One M2", "JBL", "Headphones", "Adaptive noise canceling wireless over-ear headphones.", 299.00));
        productInfos.add(new ProductInfo("Bowers & Wilkins Px8", "Bowers & Wilkins", "Headphones", "Flagship luxury noise canceling headphones.", 699.00));

        // Earbuds (18)
        productInfos.add(new ProductInfo("Apple AirPods Pro 2", "Apple", "Earbuds", "Active noise canceling earbuds with MagSafe USB-C case and H2 chip.", 249.00));
        productInfos.add(new ProductInfo("Sony WF-1000XM5", "Sony", "Earbuds", "Premium active noise canceling wireless earbuds with high-res audio.", 299.00));
        productInfos.add(new ProductInfo("Bose QuietComfort Ultra Earbuds", "Bose", "Earbuds", "World class noise cancellation in a compact earbud format.", 299.00));
        productInfos.add(new ProductInfo("Samsung Galaxy Buds2 Pro", "Samsung", "Earbuds", "Seamless integration with Samsung devices and 24-bit audio.", 229.00));
        productInfos.add(new ProductInfo("Google Pixel Buds Pro", "Google", "Earbuds", "ANC earbuds with multipoint connectivity and hands-free Assistant.", 199.00));
        productInfos.add(new ProductInfo("Beats Fit Pro", "Beats", "Earbuds", "Fitness oriented earbuds with secure-fit wingtips and spatial audio.", 199.00));
        productInfos.add(new ProductInfo("Sennheiser Momentum True Wireless 4", "Sennheiser", "Earbuds", "True wireless earbuds with lossless audio support.", 299.00));
        productInfos.add(new ProductInfo("Jabra Elite 10", "Jabra", "Earbuds", "Comfortable work and music earbuds with Dolby Atmos support.", 249.00));
        productInfos.add(new ProductInfo("Nothing Ear 2", "Nothing", "Earbuds", "Lightweight transparent earbuds with personalized sound profiles.", 149.00));
        productInfos.add(new ProductInfo("Anker Soundcore Liberty 4 NC", "Anker", "Earbuds", "Affordable earbuds with high performance noise cancellation.", 99.00));
        productInfos.add(new ProductInfo("Sony LinkBuds S", "Sony", "Earbuds", "Compact, lightweight wireless earbuds with excellent ambient sound.", 199.00));
        productInfos.add(new ProductInfo("Apple AirPods 3", "Apple", "Earbuds", "Standard wireless earbuds with spatial audio.", 169.00));
        productInfos.add(new ProductInfo("Samsung Galaxy Buds FE", "Samsung", "Earbuds", "Affordable wireless earbuds with active noise canceling.", 99.00));
        productInfos.add(new ProductInfo("Google Pixel Buds A-Series", "Google", "Earbuds", "Comfortable, low-profile earbuds with Google smarts.", 99.00));
        productInfos.add(new ProductInfo("Jabra Elite 8 Active", "Jabra", "Earbuds", "Indestructible sports earbuds with military rating.", 199.00));
        productInfos.add(new ProductInfo("Nothing Ear", "Nothing", "Earbuds", "Premium transparent earbuds with high-res audio.", 149.00));
        productInfos.add(new ProductInfo("Sony WF-C700N", "Sony", "Earbuds", "Compact noise canceling wireless earbuds.", 119.00));
        productInfos.add(new ProductInfo("Anker Soundcore Space A40", "Anker", "Earbuds", "Ultra-long battery life ANC earbuds.", 79.00));

        // Smartwatch (18)
        productInfos.add(new ProductInfo("Apple Watch Ultra 2", "Apple", "Smartwatch", "Rugged titanium smartwatch with bright display and double tap gesture.", 799.00));
        productInfos.add(new ProductInfo("Apple Watch Series 9", "Apple", "Smartwatch", "Flagship Apple watch with S9 chip and health tracking features.", 399.00));
        productInfos.add(new ProductInfo("Samsung Galaxy Watch 6 Classic", "Samsung", "Smartwatch", "Smartwatch with classic rotating bezel and comprehensive sleep coaching.", 399.00));
        productInfos.add(new ProductInfo("Garmin Fenix 7 Pro", "Garmin", "Smartwatch", "Premium multisport GPS watch with solar charging and built-in flashlight.", 799.00));
        productInfos.add(new ProductInfo("Pixel Watch 2", "Google", "Smartwatch", "Fitbit health tracking combined with Google AI smart features.", 349.00));
        productInfos.add(new ProductInfo("Fitbit Sense 2", "Fitbit", "Smartwatch", "Advanced health and fitness smartwatch with stress monitoring.", 299.00));
        productInfos.add(new ProductInfo("Amazfit GTR 4", "Amazfit", "Smartwatch", "Smartwatch with 14-day battery life and GPS tracking.", 199.00));
        productInfos.add(new ProductInfo("Garmin Venu 3", "Garmin", "Smartwatch", "GPS smartwatch with bright AMOLED display and fitness features.", 449.00));
        productInfos.add(new ProductInfo("Apple Watch SE", "Apple", "Smartwatch", "Affordable Apple watch with core fitness and safety features.", 249.00));
        productInfos.add(new ProductInfo("OnePlus Watch 2", "OnePlus", "Smartwatch", "Dual-engine architecture smartwatch with 100-hour battery life.", 299.00));
        productInfos.add(new ProductInfo("Withings ScanWatch 2", "Withings", "Smartwatch", "Hybrid smartwatch with body temperature tracking and ECG.", 349.00));
        productInfos.add(new ProductInfo("Samsung Galaxy Watch 6", "Samsung", "Smartwatch", "Sleek health-focused smartwatch with bright AMOLED.", 299.00));
        productInfos.add(new ProductInfo("Garmin Epix Gen 2", "Garmin", "Smartwatch", "Premium active outdoor smartwatch with brilliant screen.", 899.00));
        productInfos.add(new ProductInfo("Fitbit Versa 4", "Fitbit", "Smartwatch", "Fitness smartwatch with built-in Google maps and wallet.", 199.00));
        productInfos.add(new ProductInfo("Amazfit T-Rex 2", "Amazfit", "Smartwatch", "Rugged outdoor GPS smartwatch with military testing.", 229.00));
        productInfos.add(new ProductInfo("Coros Pace 3", "Coros", "Smartwatch", "Ultra-lightweight sports watch for runners.", 229.00));
        productInfos.add(new ProductInfo("Apple Watch Series 8", "Apple", "Smartwatch", "Previous gen Apple Watch with temperature sensing.", 329.00));
        productInfos.add(new ProductInfo("Garmin Forerunner 265", "Garmin", "Smartwatch", "GPS running smartwatch with AMOLED display.", 449.00));

        // Gaming Console (18)
        productInfos.add(new ProductInfo("PlayStation 5 Slim", "Sony", "Gaming Console", "Next-gen gaming console with ultra-high speed SSD and ray tracing.", 499.00));
        productInfos.add(new ProductInfo("Xbox Series X", "Microsoft", "Gaming Console", "Fastest, most powerful Xbox ever with 12 teraflops of power.", 499.00));
        productInfos.add(new ProductInfo("Xbox Series S", "Microsoft", "Gaming Console", "All-digital next-gen gaming performance in the smallest Xbox ever.", 299.00));
        productInfos.add(new ProductInfo("Nintendo Switch OLED", "Nintendo", "Gaming Console", "Handheld console with a vibrant 7-inch OLED screen and wide kickstand.", 349.00));
        productInfos.add(new ProductInfo("Steam Deck OLED", "Valve", "Gaming Console", "Powerful handheld PC gaming machine with HDR OLED screen.", 549.00));
        productInfos.add(new ProductInfo("ASUS ROG Ally", "ASUS", "Gaming Console", "Handheld gaming PC running Windows 11 with Ryzen Z1 Extreme.", 699.00));
        productInfos.add(new ProductInfo("Lenovo Legion Go", "Lenovo", "Gaming Console", "Windows handheld gaming PC with detachable controllers.", 699.00));
        productInfos.add(new ProductInfo("PlayStation VR2", "Sony", "Gaming Console", "Virtual reality headset with 4K HDR displays and feedback.", 549.00));
        productInfos.add(new ProductInfo("Meta Quest 3", "Meta", "Gaming Console", "Breakthrough mixed reality headset with high-res passthrough.", 499.00));
        productInfos.add(new ProductInfo("PlayStation Portal", "Sony", "Gaming Console", "Remote play handheld device streaming PS5 games over Wi-Fi.", 199.00));
        productInfos.add(new ProductInfo("Nintendo Switch Lite", "Nintendo", "Gaming Console", "Dedicated handheld gaming system compatible with Switch games.", 199.00));
        productInfos.add(new ProductInfo("PlayStation 5 Pro", "Sony", "Gaming Console", "Enhanced performance console with advanced ray tracing.", 699.00));
        productInfos.add(new ProductInfo("Xbox Series X Digital", "Microsoft", "Gaming Console", "All-digital Xbox Series X in white chassis.", 449.00));
        productInfos.add(new ProductInfo("Analogue Pocket", "Analogue", "Gaming Console", "Premium retro gaming multi-cartridge handheld.", 219.00));
        productInfos.add(new ProductInfo("Steam Deck LCD", "Valve", "Gaming Console", "Entry-level handheld PC gaming machine.", 399.00));
        productInfos.add(new ProductInfo("Meta Quest 2", "Meta", "Gaming Console", "Affordable standalone VR headset.", 299.00));
        productInfos.add(new ProductInfo("PlayStation VR", "Sony", "Gaming Console", "Original VR headset for PS4.", 199.00));
        productInfos.add(new ProductInfo("Asus ROG Ally X", "ASUS", "Gaming Console", "Enhanced ROG Ally with double the battery capacity.", 799.00));

        // Monitor (18)
        productInfos.add(new ProductInfo("LG UltraFine 32UL950", "LG", "Monitor", "32-inch 4K monitor with Thunderbolt 3 and Nano IPS panel.", 999.00));
        productInfos.add(new ProductInfo("Dell UltraSharp U2723QE", "Dell", "Monitor", "27-inch 4K monitor with IPS Black technology and USB-C hub.", 579.00));
        productInfos.add(new ProductInfo("Samsung Odyssey G9", "Samsung", "Monitor", "49-inch curved dual-QHD gaming monitor with 240Hz refresh rate.", 1199.00));
        productInfos.add(new ProductInfo("ASUS ROG Swift PG32UCDM", "ASUS", "Monitor", "32-inch 4K QD-OLED gaming monitor with 240Hz refresh rate.", 1299.00));
        productInfos.add(new ProductInfo("Gigabyte M27Q", "Gigabyte", "Monitor", "27-inch QHD gaming monitor with KVM switch and 170Hz screen.", 299.00));
        productInfos.add(new ProductInfo("BenQ PD2700U", "BenQ", "Monitor", "27-inch 4K monitor calibrated for designers and video editors.", 349.00));
        productInfos.add(new ProductInfo("Alienware AW3423DWF", "Alienware", "Monitor", "34-inch QD-OLED curved gaming monitor with infinite contrast.", 899.00));
        productInfos.add(new ProductInfo("LG OLED C3 42", "LG", "Monitor", "42-inch OLED TV that doubles as an incredible gaming monitor.", 899.00));
        productInfos.add(new ProductInfo("MSI Optix MAG274QRF-QD", "MSI", "Monitor", "27-inch QHD gaming monitor with Quantum Dot technology.", 379.00));
        productInfos.add(new ProductInfo("ASUS TUF VG27AQ", "ASUS", "Monitor", "27-inch QHD gaming monitor with G-SYNC compatibility.", 249.00));
        productInfos.add(new ProductInfo("ViewSonic VP2776", "ViewSonic", "Monitor", "Professional color accurate monitor with built-in color calibrator.", 799.00));
        productInfos.add(new ProductInfo("Samsung Odyssey OLED G8", "Samsung", "Monitor", "34-inch ultra-wide curved QD-OLED gaming monitor.", 999.00));
        productInfos.add(new ProductInfo("LG UltraGear 27GR95QE", "LG", "Monitor", "27-inch OLED gaming monitor with 240Hz refresh rate.", 799.00));
        productInfos.add(new ProductInfo("Dell S2721DGF", "Dell", "Monitor", "27-inch QHD gaming monitor with 165Hz screen.", 299.00));
        productInfos.add(new ProductInfo("BenQ SW271C", "BenQ", "Monitor", "Professional photo editing monitor with hardware calibration.", 1599.00));
        productInfos.add(new ProductInfo("ASUS ProArt PA278CV", "ASUS", "Monitor", "Calibrated designer monitor with daisy-chain support.", 399.00));
        productInfos.add(new ProductInfo("MSI MAG 341CQP OLED", "MSI", "Monitor", "34-inch curved QD-OLED gaming monitor.", 899.00));
        productInfos.add(new ProductInfo("Gigabyte G27F 2", "Gigabyte", "Monitor", "Affordable 1080p gaming monitor with 165Hz screen.", 179.00));

        // Keyboard (18)
        productInfos.add(new ProductInfo("Keychron K2", "Keychron", "Keyboard", "Wireless mechanical keyboard with tactile switches and RGB lighting.", 79.00));
        productInfos.add(new ProductInfo("Logitech MX Keys S", "Logitech", "Keyboard", "Wireless illuminated keyboard designed for high productivity and flow.", 109.00));
        productInfos.add(new ProductInfo("Razer BlackWidow V4", "Razer", "Keyboard", "Mechanical gaming keyboard with green clicky switches and dial.", 169.00));
        productInfos.add(new ProductInfo("Corsair K70 RGB", "Corsair", "Keyboard", "Gaming keyboard with cherry MX speed switches and aluminum frame.", 149.00));
        productInfos.add(new ProductInfo("SteelSeries Apex Pro", "SteelSeries", "Keyboard", "Mechanical keyboard with adjustable actuation switches and smart display.", 199.00));
        productInfos.add(new ProductInfo("NuPhy Air75 V2", "NuPhy", "Keyboard", "Ultra-slim wireless mechanical keyboard with low profile switches.", 119.00));
        productInfos.add(new ProductInfo("Wooting 60HE", "Wooting", "Keyboard", "Analog mechanical gaming keyboard with rapid trigger switches.", 175.00));
        productInfos.add(new ProductInfo("Asus ROG Azoth", "ASUS", "Keyboard", "Custom mechanical gaming keyboard with OLED display.", 249.00));
        productInfos.add(new ProductInfo("Epomaker TH80 Pro", "Epomaker", "Keyboard", "Hot-swappable wireless mechanical keyboard with volume knob.", 89.00));
        productInfos.add(new ProductInfo("Ducky One 3", "Ducky", "Keyboard", "Premium mechanical keyboard with hot-swappable switches and keycaps.", 119.00));
        productInfos.add(new ProductInfo("Keychron Q1 Pro", "Keychron", "Keyboard", "QMK custom wireless mechanical keyboard with full CNC metal body.", 199.00));
        productInfos.add(new ProductInfo("Logitech G915 TKL", "Logitech", "Keyboard", "Low-profile wireless mechanical gaming keyboard.", 229.00));
        productInfos.add(new ProductInfo("Keychron V1", "Keychron", "Keyboard", "Custom mechanical keyboard with volume knob.", 84.00));
        productInfos.add(new ProductInfo("Razer Huntsman V3 Pro", "Razer", "Keyboard", "Rapid trigger analog gaming keyboard.", 219.00));
        productInfos.add(new ProductInfo("SteelSeries Apex 7", "SteelSeries", "Keyboard", "Mechanical keyboard with OLED smart display.", 159.00));
        productInfos.add(new ProductInfo("NuPhy Halo75", "NuPhy", "Keyboard", "Premium high-profile wireless mechanical keyboard.", 139.00));
        productInfos.add(new ProductInfo("Logitech Signature K650", "Logitech", "Keyboard", "Comfortable full-size wireless keyboard for office.", 49.00));
        productInfos.add(new ProductInfo("Ducky One 2 Mini", "Ducky", "Keyboard", "Compact 60% mechanical gaming keyboard.", 99.00));

        // Mouse (18)
        productInfos.add(new ProductInfo("Logitech MX Master 3S", "Logitech", "Mouse", "Ergonomic wireless mouse with MagSpeed scroll wheel and 8K DPI.", 99.00));
        productInfos.add(new ProductInfo("Logitech G502 X", "Logitech", "Mouse", "Iconic gaming mouse with hybrid optical-mechanical switches.", 79.00));
        productInfos.add(new ProductInfo("Razer DeathAdder V3 Pro", "Razer", "Mouse", "Ultra-lightweight wireless gaming mouse designed for esports.", 149.00));
        productInfos.add(new ProductInfo("SteelSeries Aerox 3", "SteelSeries", "Mouse", "Water-resistant lightweight honeycomb wireless gaming mouse.", 99.00));
        productInfos.add(new ProductInfo("Razer Basilisk V3", "Razer", "Mouse", "Customizable ergonomic gaming mouse with hyperscroll wheel.", 69.00));
        productInfos.add(new ProductInfo("Logitech G Pro X Superlight 2", "Logitech", "Mouse", "Esports champion wireless gaming mouse weighing only 60 grams.", 159.00));
        productInfos.add(new ProductInfo("Pulsar X2 V2", "Pulsar", "Mouse", "Symmetrical lightweight wireless gaming mouse.", 99.00));
        productInfos.add(new ProductInfo("Glorious Model O 2", "Glorious", "Mouse", "Honeycomb lightweight wireless gaming mouse.", 99.00));
        productInfos.add(new ProductInfo("ASUS ROG Harpe Ace", "ASUS", "Mouse", "Esports wireless mouse with carbon-fiber look styling.", 129.00));
        productInfos.add(new ProductInfo("Keychron M3", "Keychron", "Mouse", "Wireless optical mouse supporting 2.4Ghz and Bluetooth.", 49.00));
        productInfos.add(new ProductInfo("Corsair Darkstar", "Corsair", "Mouse", "Wireless MMO gaming mouse with 15 programmable buttons.", 169.00));
        productInfos.add(new ProductInfo("Logitech MX Anywhere 3S", "Logitech", "Mouse", "Compact wireless mouse with silent clicks.", 79.00));
        productInfos.add(new ProductInfo("Logitech G305 LightSpeed", "Logitech", "Mouse", "Affordable high-performance wireless gaming mouse.", 49.00));
        productInfos.add(new ProductInfo("Razer Viper V3 Pro", "Razer", "Mouse", "Ultra-lightweight wireless gaming mouse with 8K polling.", 159.00));
        productInfos.add(new ProductInfo("SteelSeries Prime Wireless", "SteelSeries", "Mouse", "Co-designed with esports pros wireless mouse.", 129.00));
        productInfos.add(new ProductInfo("Pulsar Xlite V3", "Pulsar", "Mouse", "Ergonomic lightweight wireless gaming mouse.", 99.00));
        productInfos.add(new ProductInfo("Glorious Model D 2", "Glorious", "Mouse", "Lightweight ergonomic wireless gaming mouse.", 99.00));
        productInfos.add(new ProductInfo("Keychron M6", "Keychron", "Mouse", "Ergonomic wireless mouse with thumb wheel.", 49.00));

        // Storage (18)
        productInfos.add(new ProductInfo("Samsung 990 Pro 2TB", "Samsung", "Storage", "Ultra fast PCIe 4.0 NVMe M.2 SSD for gaming and content creation.", 179.00));
        productInfos.add(new ProductInfo("SanDisk Extreme Portable SSD 1TB", "SanDisk", "Storage", "Rugged portable SSD with USB 3.2 Gen 2 performance.", 99.00));
        productInfos.add(new ProductInfo("WD Black SN850X 2TB", "WD", "Storage", "High performance PCIe Gen 4 gaming SSD with optional heatsink.", 159.00));
        productInfos.add(new ProductInfo("Crucial X9 Pro 2TB", "Crucial", "Storage", "Compact high speed portable SSD for backups.", 129.00));
        productInfos.add(new ProductInfo("Seagate Backup Plus 5TB", "Seagate", "Storage", "High capacity external portable hard drive for backups.", 119.00));
        productInfos.add(new ProductInfo("WD My Book 8TB", "WD", "Storage", "Desktop external hard drive with massive storage capacity.", 169.00));
        productInfos.add(new ProductInfo("Samsung T7 Shield 2TB", "Samsung", "Storage", "Rugged external portable SSD with IP65 water resistance.", 169.00));
        productInfos.add(new ProductInfo("Kingston XS2000 1TB", "Kingston", "Storage", "Pocket-sized external SSD with up to 2000MB/s speeds.", 99.00));
        productInfos.add(new ProductInfo("Seagate FireCuda 530 2TB", "Seagate", "Storage", "PCIe Gen4 NVMe SSD fully compatible with PlayStation 5.", 189.00));
        productInfos.add(new ProductInfo("Crucial T700 2TB", "Crucial", "Storage", "Extreme speed PCIe Gen5 NVMe M.2 SSD.", 269.00));
        productInfos.add(new ProductInfo("Sabrent Rocket 4 Plus 2TB", "Sabrent", "Storage", "High performance NVMe SSD with premium heatsink.", 169.00));
        productInfos.add(new ProductInfo("Samsung 980 Pro 1TB", "Samsung", "Storage", "Highly reliable PCIe Gen4 NVMe M.2 SSD.", 109.00));
        productInfos.add(new ProductInfo("WD Blue SN580 1TB", "WD", "Storage", "Affordable PCIe Gen4 NVMe SSD for creators.", 74.00));
        productInfos.add(new ProductInfo("Crucial T500 2TB", "Crucial", "Storage", "High-performance Gen4 NVMe M.2 SSD.", 149.00));
        productInfos.add(new ProductInfo("SanDisk Professional PRO-BLADE 2TB", "SanDisk", "Storage", "Modular SSD ecosystem for professional workflows.", 279.00));
        productInfos.add(new ProductInfo("Seagate Expansion 4TB", "Seagate", "Storage", "Simple, high capacity external portable hard drive.", 99.00));
        productInfos.add(new ProductInfo("Lexar NM790 2TB", "Lexar", "Storage", "High-speed PCIe Gen4 NVMe M.2 SSD.", 139.00));
        productInfos.add(new ProductInfo("Sabrent Rocket Nano 2TB", "Sabrent", "Storage", "Ultra-compact external aluminum SSD.", 189.00));

        // Networking (18)
        productInfos.add(new ProductInfo("Netgear Nighthawk RAX200", "Netgear", "Networking", "Tri-band WiFi 6 router with up to 11Gbps speeds.", 499.00));
        productInfos.add(new ProductInfo("ASUS RT-AX88U Pro", "ASUS", "Networking", "Dual-band WiFi 6 gaming router with dual 2.5G ports.", 299.00));
        productInfos.add(new ProductInfo("TP-Link Deco XE75", "TP-Link", "Networking", "AXE5400 Tri-band WiFi 6E mesh system covering up to 5500 sq ft.", 319.00));
        productInfos.add(new ProductInfo("Linksys Velop Pro 6E", "Linksys", "Networking", "Cognitive mesh WiFi 6E router with easy setup.", 199.00));
        productInfos.add(new ProductInfo("Google Nest WiFi Pro", "Google", "Networking", "WiFi 6E mesh router system with smart home integration.", 299.00));
        productInfos.add(new ProductInfo("Netgear Orbi CBK752", "Netgear", "Networking", "Tri-band mesh WiFi 6 system with integrated cable modem.", 449.00));
        productInfos.add(new ProductInfo("TP-Link Archer AX10000", "TP-Link", "Networking", "Next-gen gaming tri-band router with 8 gigabit ports.", 349.00));
        productInfos.add(new ProductInfo("ASUS ZenWiFi BQ16 Pro", "ASUS", "Networking", "Quad-band WiFi 7 mesh system for ultra fast smart homes.", 699.00));
        productInfos.add(new ProductInfo("Synology RT6600ax", "Synology", "Networking", "Security focused WiFi 6 router with SRM operating system.", 299.00));
        productInfos.add(new ProductInfo("Ubiquiti UniFi Express", "Ubiquiti", "Networking", "Compact cloud-managed gateway and WiFi 6 access point.", 149.00));
        productInfos.add(new ProductInfo("GL.iNet GL-MT3000", "GL.iNet", "Networking", "Pocket sized WiFi 6 travel router with built-in VPN support.", 89.00));
        productInfos.add(new ProductInfo("TP-Link Archer AX55", "TP-Link", "Networking", "Affordable dual-band WiFi 6 router.", 119.00));
        productInfos.add(new ProductInfo("Netgear Nighthawk RAX5400", "Netgear", "Networking", "High performance WiFi 6 router for streaming.", 199.00));
        productInfos.add(new ProductInfo("ASUS ROG Rapture GT-AXE16000", "ASUS", "Networking", "Extreme performance quad-band WiFi 6E gaming router.", 599.00));
        productInfos.add(new ProductInfo("Linksys Hydra Pro 6E", "Linksys", "Networking", "High-capacity WiFi 6E router for multiple devices.", 249.00));
        productInfos.add(new ProductInfo("Ubiquiti Dream Router", "Ubiquiti", "Networking", "All-in-one console router with PoE switch.", 199.00));
        productInfos.add(new ProductInfo("Eero Max 7", "Amazon", "Networking", "High speed WiFi 7 tri-band mesh system.", 599.00));
        productInfos.add(new ProductInfo("GL.iNet GL-AXT1800", "GL.iNet", "Networking", "Powerful slate WiFi 6 travel router.", 129.00));

        // Camera (18)
        productInfos.add(new ProductInfo("Sony Alpha 7 IV", "Sony", "Camera", "Full-frame mirrorless camera with 33MP sensor and 4K 60p video.", 2499.00));
        productInfos.add(new ProductInfo("Canon EOS R6 Mark II", "Canon", "Camera", "Versatile full-frame mirrorless camera with high speed shooting.", 2299.00));
        productInfos.add(new ProductInfo("Fujifilm X-T5", "Fujifilm", "Camera", "Classic retro mirrorless camera with 40MP APS-C sensor.", 1699.00));
        productInfos.add(new ProductInfo("Nikon Z6 II", "Nikon", "Camera", "Full-frame mirrorless camera with dual processors and dual card slots.", 1599.00));
        productInfos.add(new ProductInfo("GoPro Hero 12 Black", "GoPro", "Camera", "Ultimate action camera with HyperSmooth stabilization and HDR video.", 399.00));
        productInfos.add(new ProductInfo("DJI Osmo Pocket 3", "DJI", "Camera", "Gimbal stabilized camera with large 1-inch CMOS sensor.", 519.00));
        productInfos.add(new ProductInfo("Panasonic Lumix S5 II", "Panasonic", "Camera", "Full-frame mirrorless camera with phase hybrid autofocus.", 1799.00));
        productInfos.add(new ProductInfo("Sony ZV-E10", "Sony", "Camera", "Vlogging mirrorless camera with interchangeable lens mount.", 699.00));
        productInfos.add(new ProductInfo("Canon EOS R100", "Canon", "Camera", "Affordable entry-level APS-C mirrorless camera.", 479.00));
        productInfos.add(new ProductInfo("Nikon Z30", "Nikon", "Camera", "APS-C mirrorless camera designed for creators and vloggers.", 659.00));
        productInfos.add(new ProductInfo("Insta360 X3", "Insta360", "Camera", "360-degree action camera with active HDR and invisible selfie stick.", 449.00));
        productInfos.add(new ProductInfo("Fujifilm X100VI", "Fujifilm", "Camera", "Compact premium street photography camera with 40MP.", 1599.00));
        productInfos.add(new ProductInfo("Sony Alpha 7R V", "Sony", "Camera", "Ultra-high resolution full-frame camera with 61MP.", 3899.00));
        productInfos.add(new ProductInfo("Canon EOS R5", "Canon", "Camera", "Professional mirrorless camera with 8K video.", 3399.00));
        productInfos.add(new ProductInfo("Nikon Z8", "Nikon", "Camera", "Flagship hybrid camera in a compact build.", 3799.00));
        productInfos.add(new ProductInfo("DJI Action 4", "DJI", "Camera", "Rugged action camera with 1/1.3-inch sensor.", 299.00));
        productInfos.add(new ProductInfo("Panasonic Lumix G9 II", "Panasonic", "Camera", "High-speed Micro Four Thirds mirrorless camera.", 1899.00));
        productInfos.add(new ProductInfo("Insta360 Ace Pro", "Insta360", "Camera", "Wide-angle action camera co-engineered with Leica.", 399.00));

        // Accessory (18)
        productInfos.add(new ProductInfo("Anker 737 Power Bank", "Anker", "Accessory", "High capacity power bank with 140W fast charging and smart display.", 149.00));
        productInfos.add(new ProductInfo("Apple MagSafe Charger", "Apple", "Accessory", "Fast wireless charger with magnetic alignment for iPhones.", 39.00));
        productInfos.add(new ProductInfo("Belkin 3-in-1 Wireless Charger", "Belkin", "Accessory", "Charging stand for iPhone, Apple Watch, and AirPods.", 149.00));
        productInfos.add(new ProductInfo("Elgato Stream Deck MK.2", "Elgato", "Accessory", "Studio controller with 15 customizable LCD keys for streaming.", 149.00));
        productInfos.add(new ProductInfo("Logitech Brio 4K", "Logitech", "Accessory", "Premium 4K webcam with HDR and RightLight 3 support.", 199.00));
        productInfos.add(new ProductInfo("Anker Prime 6-in-1 Charging Station", "Anker", "Accessory", "Desktop charging station with dual USB-C, dual USB-A, and AC outlets.", 99.00));
        productInfos.add(new ProductInfo("Apple AirTag 4-Pack", "Apple", "Accessory", "Smart trackers compatible with Find My network.", 99.00));
        productInfos.add(new ProductInfo("Samsung SmartTag2", "Samsung", "Accessory", "Bluetooth smart trackers compatible with SmartThings network.", 29.00));
        productInfos.add(new ProductInfo("Satechi USB4 Multiport Adapter", "Satechi", "Accessory", "Multiport hub with 8K HDMI, Ethernet, and USB-C power delivery.", 149.00));
        productInfos.add(new ProductInfo("Twelve South Curve", "Twelve South", "Accessory", "Ergonomic aluminum stand for MacBooks and laptops.", 59.00));
        productInfos.add(new ProductInfo("Peak Design Everyday Backpack 20L", "Peak Design", "Accessory", "Premium everyday carry and photography gear backpack.", 279.00));
        productInfos.add(new ProductInfo("Anker Nano Power Bank", "Anker", "Accessory", "Compact pocket power bank with built-in connector.", 29.00));
        productInfos.add(new ProductInfo("Elgato Wave 3", "Elgato", "Accessory", "Premium USB microphone for streaming and podcasting.", 149.00));
        productInfos.add(new ProductInfo("Logitech MX Brio 4K", "Logitech", "Accessory", "Next-gen ultra HD streaming webcam.", 199.00));
        productInfos.add(new ProductInfo("Peak Design Tech Pouch", "Peak Design", "Accessory", "Organized pouch for cables and chargers.", 59.00));
        productInfos.add(new ProductInfo("Satechi Dual Dock Stand", "Satechi", "Accessory", "Laptop stand with NVMe SSD enclosure slot.", 149.00));
        productInfos.add(new ProductInfo("Elgato Key Light Air", "Elgato", "Accessory", "App-controlled professional studio lighting panel.", 129.00));
        productInfos.add(new ProductInfo("DJI Mic 2", "DJI", "Accessory", "High-quality wireless microphone system for creators.", 349.00));

        Random rand = new Random();

        for (ProductInfo info : productInfos) {
            // Save Product
            ProductEntity product = ProductEntity.builder()
                    .name(info.name)
                    .brand(info.brand)
                    .category(info.category)
                    .description(info.description)
                    .archived(false)
                    .build();
            product = productRepository.save(product);

            // Seed prices for 3 different sellers
            List<SellerEntity> selectedSellers = new ArrayList<>(sellers);
            Collections.shuffle(selectedSellers);

            BigDecimal basePrice = BigDecimal.valueOf(info.basePrice);
            
            for (int s = 0; s < 3; s++) {
                SellerEntity seller = selectedSellers.get(s);

                // Different sellers have slightly different discounts and current prices
                double discountFactor = 0.90 + (0.10 * rand.nextDouble()); // 0% to 10% discount
                BigDecimal originalPrice = basePrice.multiply(BigDecimal.valueOf(1.05 + (0.10 * rand.nextDouble())))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal currentPrice = originalPrice.multiply(BigDecimal.valueOf(discountFactor))
                        .setScale(2, RoundingMode.HALF_UP);

                if (currentPrice.compareTo(originalPrice) > 0) {
                    currentPrice = originalPrice;
                }

                ProductPriceEntity price = ProductPriceEntity.builder()
                        .product(product)
                        .seller(seller)
                        .currentPrice(currentPrice)
                        .originalPrice(originalPrice)
                        .productUrl(seller.getWebsiteUrl() + "/product/" + product.getId())
                        .lastUpdated(LocalDateTime.now())
                        .build();
                
                // Note: PrePersist calculates discount percentage automatically
                productPriceRepository.save(price);

                // Generate 30-90 days of price history
                int historyDays = 30 + rand.nextInt(60);
                LocalDateTime changedAt = LocalDateTime.now().minusDays(historyDays);
                BigDecimal lastHistPrice = originalPrice.multiply(BigDecimal.valueOf(1.10)) // Start history slightly higher
                        .setScale(2, RoundingMode.HALF_UP);

                for (int d = 0; d < historyDays; d += 3 + rand.nextInt(7)) {
                    changedAt = changedAt.plusDays(3 + rand.nextInt(7));
                    if (changedAt.isAfter(LocalDateTime.now())) {
                        break;
                    }

                    double stepFactor = 0.95 + (0.10 * rand.nextDouble()); // change by ±5%
                    BigDecimal nextPrice = lastHistPrice.multiply(BigDecimal.valueOf(stepFactor))
                            .setScale(2, RoundingMode.HALF_UP);

                    BigDecimal diff = nextPrice.subtract(lastHistPrice);
                    BigDecimal pct = diff.divide(lastHistPrice, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);

                    PriceHistoryEntity hist = PriceHistoryEntity.builder()
                            .product(product)
                            .seller(seller)
                            .oldPrice(lastHistPrice)
                            .newPrice(nextPrice)
                            .priceDifference(diff)
                            .changePercentage(pct)
                            .changedAt(changedAt)
                            .build();

                    priceHistoryRepository.save(hist);
                    lastHistPrice = nextPrice;
                }
            }

            // Create Product Analytics
            int viewCount = 100 + rand.nextInt(4000);
            int saveCount = 10 + rand.nextInt(150);
            int watchlistCount = 5 + rand.nextInt(80);
            int priceChangeCount = 5 + rand.nextInt(15);

            ProductAnalyticsEntity analytics = ProductAnalyticsEntity.builder()
                    .product(product)
                    .viewCount(viewCount)
                    .saveCount(saveCount)
                    .watchlistCount(watchlistCount)
                    .priceChangeCount(priceChangeCount)
                    .lastViewedAt(LocalDateTime.now().minusHours(rand.nextInt(48)))
                    .build();
            analyticsRepository.save(analytics);

            // Generate synthetic interaction events for users
            for (UserEntity user : users) {
                if (rand.nextDouble() < 0.3) { // 30% chance user interacted with this product
                    InteractionType[] types = {InteractionType.PRODUCT_VIEW, InteractionType.SEARCH, InteractionType.SELLER_CLICK};
                    InteractionType type = types[rand.nextInt(types.length)];

                    Map<String, Object> metadata = Map.of();
                    if (type == InteractionType.SEARCH) {
                        metadata = Map.of("keyword", product.getName());
                    }

                    UserInteractionEventEntity event = UserInteractionEventEntity.builder()
                            .user(user)
                            .product(product)
                            .interactionType(type)
                            .metadata(metadata)
                            .createdAt(LocalDateTime.now().minusDays(rand.nextInt(30)))
                            .build();
                    interactionRepository.save(event);
                }
            }
        }
    }

    private static class ProductInfo {
        String name;
        String brand;
        String category;
        String description;
        double basePrice;

        ProductInfo(String name, String brand, String category, String description, double basePrice) {
            this.name = name;
            this.brand = brand;
            this.category = category;
            this.description = description;
            this.basePrice = basePrice;
        }
    }
}
