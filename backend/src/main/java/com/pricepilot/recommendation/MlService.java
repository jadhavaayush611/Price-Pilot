package com.pricepilot.recommendation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.recommendation.engine.*;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MlService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final SavedProductRepository savedProductRepository;
    private final PriceWatchlistRepository watchlistRepository;
    private final RecommendationProfileService profileService;
    private final RuleBasedRecommendationEngine ruleBasedEngine;
    private final PopularityRecommendationEngine popularityEngine;
    private final ContentBasedRecommendationEngine contentEngine;
    private final CollaborativeFilteringEngine collaborativeEngine;
    private final HybridRecommendationEngine hybridEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MlService(
            ProductRepository productRepository,
            ProductPriceRepository productPriceRepository,
            SavedProductRepository savedProductRepository,
            PriceWatchlistRepository watchlistRepository,
            RecommendationProfileService profileService,
            RuleBasedRecommendationEngine ruleBasedEngine,
            PopularityRecommendationEngine popularityEngine,
            ContentBasedRecommendationEngine contentEngine,
            CollaborativeFilteringEngine collaborativeEngine,
            HybridRecommendationEngine hybridEngine) {
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
        this.savedProductRepository = savedProductRepository;
        this.watchlistRepository = watchlistRepository;
        this.profileService = profileService;
        this.ruleBasedEngine = ruleBasedEngine;
        this.popularityEngine = popularityEngine;
        this.contentEngine = contentEngine;
        this.collaborativeEngine = collaborativeEngine;
        this.hybridEngine = hybridEngine;
    }

    private Path getModelsDir() {
        return Paths.get("models", "recommendation").toAbsolutePath().normalize();
    }

    public Map<String, Object> train(String datasetVersion, int k) throws IOException, InterruptedException {
        // Determine the location of the workspace relative to current directory
        // Usually, the app runs in the "backend" directory when starting from maven, so workspace root is parent ".."
        String pythonExe = Paths.get("..", "pricepilot-python-sdk", ".venv", "Scripts", "python.exe")
                .toAbsolutePath().normalize().toString();
        String scriptPath = Paths.get("..", "pricepilot_ml", "train.py")
                .toAbsolutePath().normalize().toString();
        String baseDir = "..";

        File pythonFile = new File(pythonExe);
        if (!pythonFile.exists()) {
            // Try current directory fallback (in case running directly from root)
            pythonExe = Paths.get("pricepilot-python-sdk", ".venv", "Scripts", "python.exe")
                    .toAbsolutePath().normalize().toString();
            scriptPath = Paths.get("pricepilot_ml", "train.py")
                    .toAbsolutePath().normalize().toString();
            baseDir = ".";
            pythonFile = new File(pythonExe);
            if (!pythonFile.exists()) {
                pythonExe = "python";
            }
        }

        ProcessBuilder pb = new ProcessBuilder(
                pythonExe,
                scriptPath,
                "--dataset-version", datasetVersion,
                "--k", String.valueOf(k),
                "--base-dir", baseDir
        );
        pb.directory(new File(baseDir));
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Python training pipeline failed with exit code: " + exitCode);
        }

        return getTrainingReport();
    }

    public Map<String, Object> getTrainingReport() throws IOException {
        Path reportPath = getModelsDir().resolve("training_report.json");
        if (!Files.exists(reportPath)) {
            // Try parent fallback if running from backend folder
            reportPath = Paths.get("..", "models", "recommendation", "training_report.json").toAbsolutePath().normalize();
            if (!Files.exists(reportPath)) {
                throw new IOException("Training report not found.");
            }
        }
        return objectMapper.readValue(reportPath.toFile(), new TypeReference<Map<String, Object>>() {});
    }

    public Map<String, Object> getModelMetadata(String algorithm) throws IOException {
        String filename = algorithm.toLowerCase().replace(" ", "_") + "_metadata.json";
        Path metaPath = getModelsDir().resolve(filename);
        if (!Files.exists(metaPath)) {
            metaPath = Paths.get("..", "models", "recommendation", filename).toAbsolutePath().normalize();
            if (!Files.exists(metaPath)) {
                throw new IOException("Metadata for " + algorithm + " not found.");
            }
        }
        return objectMapper.readValue(metaPath.toFile(), new TypeReference<Map<String, Object>>() {});
    }

    public Map<String, Object> evaluate(String algorithm, int k) throws IOException {
        return getModelMetadata(algorithm);
    }

    public List<ProductResponseDTO> predict(String algorithm, UUID userId, int limit) {
        Set<UUID> excludedIds = new HashSet<>();
        List<SavedProductEntity> saved = savedProductRepository.findAllByUserIdWithProduct(userId);
        for (SavedProductEntity sp : saved) {
            if (sp.getProduct() != null) excludedIds.add(sp.getProduct().getId());
        }
        List<PriceWatchlistEntity> watchlists = watchlistRepository.findAllByUserIdWithProduct(userId);
        for (PriceWatchlistEntity pw : watchlists) {
            if (pw.getProduct() != null) excludedIds.add(pw.getProduct().getId());
        }
        if (excludedIds.isEmpty()) {
            excludedIds.add(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        }

        RecommendationProfile profile = profileService.getProfile(userId);
        List<ProductEntity> candidates = productRepository.findActiveProductsExcluding(excludedIds, PageRequest.of(0, 100));

        RecommendationEngine engine;
        switch (algorithm.toUpperCase().trim()) {
            case "POPULARITY":
                engine = popularityEngine;
                break;
            case "CONTENT":
                engine = contentEngine;
                break;
            case "COLLABORATIVE":
                engine = collaborativeEngine;
                break;
            case "HYBRID":
                engine = hybridEngine;
                break;
            case "RULE_BASED":
            default:
                engine = ruleBasedEngine;
                break;
        }

        List<ScoredProduct> scored = engine.recommend(userId, candidates, profile, saved, watchlists, limit);
        List<ProductEntity> recommendedProducts = scored.stream().map(ScoredProduct::getProduct).collect(Collectors.toList());
        List<ProductResponseDTO> dtos = mapProductsToResponseDTOs(recommendedProducts);

        Map<UUID, ScoredProduct> scoredMap = scored.stream().collect(Collectors.toMap(s -> s.getProduct().getId(), s -> s));
        for (ProductResponseDTO dto : dtos) {
            ScoredProduct sp = scoredMap.get(dto.getId());
            if (sp != null) {
                dto.setRecommendationScore(sp.getScore());
                dto.setRecommendationAlgorithm(engine.getAlgorithmName());
                dto.setRecommendationReasons(sp.getReasons());
            }
        }
        return dtos;
    }

    private List<ProductResponseDTO> mapProductsToResponseDTOs(List<ProductEntity> products) {
        if (products.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> productIds = products.stream().map(ProductEntity::getId).collect(Collectors.toList());
        var prices = productPriceRepository.findPricesWithSellersByProductIds(productIds);
        Map<UUID, List<ProductPriceEntity>> pricesByProductId = prices.stream()
                .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

        return products.stream().map(product -> {
            ProductResponseDTO dto = ProductResponseDTO.fromEntity(product);
            var productPrices = pricesByProductId.getOrDefault(product.getId(), List.of());
            dto.setPrices(productPrices.stream()
                    .map(priceEntity -> com.pricepilot.productprice.dto.ProductPriceResponseDTO.builder()
                            .id(priceEntity.getId())
                            .currentPrice(priceEntity.getCurrentPrice())
                            .originalPrice(priceEntity.getOriginalPrice())
                            .discountPercentage(priceEntity.getDiscountPercentage())
                            .productUrl(priceEntity.getProductUrl())
                            .lastUpdated(priceEntity.getLastUpdated())
                            .seller(com.pricepilot.seller.dto.SellerResponseDTO.fromEntity(priceEntity.getSeller()))
                            .createdAt(priceEntity.getCreatedAt())
                            .updatedAt(priceEntity.getUpdatedAt())
                            .build()
                    )
                    .collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }
}
