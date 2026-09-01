package com.pricepilot.intelligence.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.ai.AiClient;
import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.alert.service.PriceAlertService;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.assistant.dto.*;
import com.pricepilot.intelligence.assistant.fallback.DeterministicShoppingAssistantFallback;
import com.pricepilot.intelligence.assistant.intent.PromptInjectionProtector;
import com.pricepilot.intelligence.assistant.intent.ShoppingIntentClassifier;
import com.pricepilot.intelligence.assistant.model.AssistantConversationEntity;
import com.pricepilot.intelligence.assistant.model.AssistantMessageEntity;
import com.pricepilot.intelligence.assistant.model.MessageRole;
import com.pricepilot.intelligence.assistant.repository.AssistantConversationRepository;
import com.pricepilot.intelligence.assistant.repository.AssistantMessageRepository;
import com.pricepilot.intelligence.comparison.ComparisonService;
import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.service.SearchDiscoveryService;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import com.pricepilot.intelligence.recommendation.RecommendationService;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.intelligence.recommendation.dto.RecommendationCompareRequest;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.PriceWatchlistService;
import com.pricepilot.watchlist.dto.WatchlistResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShoppingAssistantServiceImpl implements ShoppingAssistantService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingAssistantServiceImpl.class);

    private final AssistantConversationRepository conversationRepository;
    private final AssistantMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final SearchDiscoveryService searchDiscoveryService;
    private final ComparisonService comparisonService;
    private final PriceAnalyticsService priceAnalyticsService;
    private final PriceWatchlistService priceWatchlistService;
    private final PriceAlertService priceAlertService;
    private final UserShoppingPreferenceService preferenceService;
    private final RecommendationService recommendationService;
    private final ShoppingIntentClassifier intentClassifier;
    private final PromptInjectionProtector promptProtector;
    private final DeterministicShoppingAssistantFallback fallbackGenerator;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public ShoppingAssistantServiceImpl(
            AssistantConversationRepository conversationRepository,
            AssistantMessageRepository messageRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            ProductPriceRepository productPriceRepository,
            SearchDiscoveryService searchDiscoveryService,
            ComparisonService comparisonService,
            PriceAnalyticsService priceAnalyticsService,
            PriceWatchlistService priceWatchlistService,
            PriceAlertService priceAlertService,
            UserShoppingPreferenceService preferenceService,
            RecommendationService recommendationService,
            ShoppingIntentClassifier intentClassifier,
            PromptInjectionProtector promptProtector,
            DeterministicShoppingAssistantFallback fallbackGenerator,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AiClient aiClient) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
        this.searchDiscoveryService = searchDiscoveryService;
        this.comparisonService = comparisonService;
        this.priceAnalyticsService = priceAnalyticsService;
        this.priceWatchlistService = priceWatchlistService;
        this.priceAlertService = priceAlertService;
        this.preferenceService = preferenceService;
        this.recommendationService = recommendationService;
        this.intentClassifier = intentClassifier;
        this.promptProtector = promptProtector;
        this.fallbackGenerator = fallbackGenerator;
        this.aiClient = aiClient;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Override
    @Transactional
    public AssistantConversationDTO createConversation(UUID userId, CreateConversationRequest request) {
        UserEntity user = getUserOrThrow(userId);

        AssistantConversationEntity conversation = AssistantConversationEntity.builder()
                .user(user)
                .title(request.getTitle() != null && !request.getTitle().trim().isEmpty() 
                        ? request.getTitle().trim() : "New Shopping Conversation")
                .build();

        conversation = conversationRepository.save(conversation);

        List<AssistantMessageDTO> messages = new ArrayList<>();
        if (request.getInitialMessage() != null && !request.getInitialMessage().trim().isEmpty()) {
            AssistantResponseDTO res = sendMessage(conversation.getId(), userId, 
                    new SendMessageRequest(request.getInitialMessage().trim(), null));
            AssistantMessageEntity userMsg = messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId()).get(0);
            messages.add(AssistantMessageDTO.fromEntity(userMsg, null, null));
            messages.add(new AssistantMessageDTO(
                    res.getMessageId(), conversation.getId(), MessageRole.ASSISTANT,
                    res.getResponse(), res.getIntent(), res.getEvidenceBundle(), res.getProducts(), LocalDateTime.now()
            ));
        }

        return AssistantConversationDTO.fromEntity(conversation, messages);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistantConversationDTO> listConversations(UUID userId) {
        getUserOrThrow(userId);
        List<AssistantConversationEntity> list = conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId);
        return list.stream()
                .map(c -> AssistantConversationDTO.fromEntity(c, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AssistantConversationDTO getConversation(UUID conversationId, UUID userId) {
        getUserOrThrow(userId);
        AssistantConversationEntity conv = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        List<AssistantMessageEntity> messages = messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId);

        List<AssistantMessageDTO> messageDTOs = messages.stream().map(m -> {
            AssistantEvidenceBundle bundle = deserializeBundle(m.getEvidenceBundle());
            Object payload = deserializePayload(m.getPayload());
            return AssistantMessageDTO.fromEntity(m, bundle, payload);
        }).collect(Collectors.toList());

        return AssistantConversationDTO.fromEntity(conv, messageDTOs);
    }

    @Override
    @Transactional
    public void deleteConversation(UUID conversationId, UUID userId) {
        getUserOrThrow(userId);
        AssistantConversationEntity conv = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));
        conversationRepository.delete(conv);
    }

    @Override
    @Transactional
    public AssistantResponseDTO sendMessage(UUID conversationId, UUID userId, SendMessageRequest request) {
        long startTime = System.currentTimeMillis();
        UserEntity user = getUserOrThrow(userId);
        AssistantConversationEntity conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        String rawContent = request.getContent();
        String sanitizedContent = promptProtector.sanitizeUserInput(rawContent);

        // 1. Save user message
        AssistantMessageEntity userMessage = AssistantMessageEntity.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(rawContent)
                .build();
        userMessage = messageRepository.save(userMessage);
        conversation.getMessages().add(userMessage);

        // 2. Classify intent
        AssistantIntent intent = intentClassifier.classifyIntent(sanitizedContent);

        // 3. Extract user preferences & signals (Phase 8)
        UserShoppingPreferenceDTO preferences = null;
        try {
            preferences = preferenceService.getPreferences(userId);
        } catch (Exception e) {
            log.warn("Unable to fetch preferences for user {}: {}", userId, e.getMessage());
        }

        // 4. Build grounded evidence bundle
        AssistantEvidenceBundle bundle = buildEvidenceBundle(intent, sanitizedContent, request.getActiveProductId(), user, preferences);

        // 5. Generate assistant response (AI service with deterministic fallback)
        String responseText = generateAssistantText(sanitizedContent, conversation.getId(), intent, bundle, preferences);

        // 6. Save assistant message
        String serializedBundle = serializeBundle(bundle);
        String serializedProducts = serializePayload(bundle.getGroundedProducts());

        AssistantMessageEntity assistantMessage = AssistantMessageEntity.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(responseText)
                .intent(intent)
                .evidenceBundle(serializedBundle)
                .payload(serializedProducts)
                .build();
        assistantMessage = messageRepository.save(assistantMessage);
        conversation.getMessages().add(assistantMessage);

        // Update conversation updated_at
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Assistant message processed | conv_id={} | intent={} | grounded_items={} | duration_ms={}",
                conversationId, intent, bundle.getGroundedProducts().size(), durationMs);

        // 7. Format backward-compatible response DTO
        return buildResponseDTO(conversation.getId(), assistantMessage.getId(), intent, responseText, bundle);
    }

    @Override
    @Transactional
    public AssistantResponseDTO processDirectChat(UUID userId, String message, String conversationIdStr) {
        UUID convId = resolveOrCreateConversation(userId, conversationIdStr, message);
        return sendMessage(convId, userId, new SendMessageRequest(message, null));
    }

    @Override
    @Transactional
    public AssistantResponseDTO processDirectCompare(UUID userId, List<UUID> productIds, String conversationIdStr) {
        UUID convId = resolveOrCreateConversation(userId, conversationIdStr, "Product Comparison");
        String prompt = "Compare these products: " + (productIds != null ? productIds.toString() : "");
        return sendMessage(convId, userId, new SendMessageRequest(prompt, null));
    }

    @Override
    @Transactional
    public AssistantResponseDTO processDirectAsk(UUID userId, String question, String conversationIdStr) {
        UUID convId = resolveOrCreateConversation(userId, conversationIdStr, question);
        return sendMessage(convId, userId, new SendMessageRequest(question, null));
    }

    @Override
    @Transactional
    public void clearConversationMemory(UUID userId, String conversationIdStr) {
        if (conversationIdStr != null && !conversationIdStr.trim().isEmpty()) {
            try {
                UUID convId = UUID.fromString(conversationIdStr);
                conversationRepository.findByIdAndUserId(convId, userId).ifPresent(c -> {
                    messageRepository.deleteAll(c.getMessages());
                    c.getMessages().clear();
                    conversationRepository.save(c);
                });
            } catch (IllegalArgumentException ignored) {}
        }
    }

    // --- Private Orchestration Helpers ---

    private UUID resolveOrCreateConversation(UUID userId, String conversationIdStr, String title) {
        if (conversationIdStr != null && !conversationIdStr.trim().isEmpty()) {
            try {
                UUID parsed = UUID.fromString(conversationIdStr);
                Optional<AssistantConversationEntity> existing = conversationRepository.findByIdAndUserId(parsed, userId);
                if (existing.isPresent()) {
                    return existing.get().getId();
                }
            } catch (IllegalArgumentException ignored) {}
        }
        // Create new conversation
        String cleanTitle = title != null && title.length() > 50 ? title.substring(0, 47) + "..." : title;
        AssistantConversationDTO created = createConversation(userId, new CreateConversationRequest(cleanTitle, null));
        return created.getId();
    }

    private AssistantEvidenceBundle buildEvidenceBundle(
            AssistantIntent intent,
            String query,
            UUID activeProductId,
            UserEntity user,
            UserShoppingPreferenceDTO preferences) {

        AssistantEvidenceBundle bundle = AssistantEvidenceBundle.builder()
                .intent(intent)
                .factualEvidence(new ArrayList<>())
                .personalizationReasoning(new ArrayList<>())
                .tradeOffs(new ArrayList<>())
                .unknownOrInsufficientData(new ArrayList<>())
                .groundedProducts(new ArrayList<>())
                .suggestedActions(new ArrayList<>())
                .confidenceScore(0.85)
                .build();

        switch (intent) {
            case DISCOVERY -> populateDiscoveryEvidence(bundle, query, preferences);
            case COMPARISON -> populateComparisonEvidence(bundle, query, activeProductId, user);
            case PRICE_ANALYSIS -> populatePriceAnalysisEvidence(bundle, query, activeProductId);
            case RECOMMENDATION -> populateRecommendationEvidence(bundle, user.getId(), preferences);
            case WATCHLIST_ACTION -> populateWatchlistEvidence(bundle, user);
            case PREFERENCE_QUERY -> populatePreferenceEvidence(bundle, preferences);
            default -> populateGeneralEvidence(bundle);
        }

        return bundle;
    }

    private void populateDiscoveryEvidence(
            AssistantEvidenceBundle bundle,
            String query,
            UserShoppingPreferenceDTO preferences) {

        Double priceConstraint = intentClassifier.extractPriceConstraint(query);
        BigDecimal maxPrice = priceConstraint != null ? BigDecimal.valueOf(priceConstraint) : null;
        if (maxPrice == null && preferences != null && preferences.getMaxBudget() != null) {
            maxPrice = preferences.getMaxBudget();
        }

        DiscoverySearchRequestDTO req = DiscoverySearchRequestDTO.builder()
                .query(query)
                .maxPrice(maxPrice)
                .page(0)
                .size(5)
                .build();

        try {
            DiscoverySearchResponseDTO discRes = searchDiscoveryService.searchAndDiscover(req);
            if (discRes != null && discRes.getContent() != null && !discRes.getContent().isEmpty()) {
                for (DiscoveryProductDTO p : discRes.getContent()) {
                    Map<String, Object> card = new HashMap<>();
                    card.put("id", p.getId().toString());
                    card.put("name", p.getName());
                    card.put("brand", p.getBrand());
                    card.put("category", p.getCategory());
                    card.put("price", p.getCurrentBestPrice() != null ? p.getCurrentBestPrice().doubleValue() : null);
                    card.put("originalPrice", p.getOriginalPrice() != null ? p.getOriginalPrice().doubleValue() : null);
                    card.put("discount", p.getDiscountPercentage() != null ? p.getDiscountPercentage().doubleValue() : 0.0);
                    card.put("dealQuality", p.getPrices() != null && !p.getPrices().isEmpty() ? "AVAILABLE" : "UNKNOWN");
                    bundle.getGroundedProducts().add(card);

                    bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                            .productId(p.getId())
                            .productName(p.getName())
                            .factType("PRICE")
                            .description(String.format("Current price: $%s (Discount: %s%%)", 
                                    p.getCurrentBestPrice() != null ? p.getCurrentBestPrice() : "N/A", 
                                    p.getDiscountPercentage() != null ? p.getDiscountPercentage() : "0"))
                            .factualValue(p.getCurrentBestPrice())
                            .verified(true)
                            .confidence(0.95)
                            .build());

                    if (preferences != null) {
                        if (preferences.getPreferredBrands() != null && p.getBrand() != null
                                && preferences.getPreferredBrands().stream().anyMatch(b -> b.equalsIgnoreCase(p.getBrand()))) {
                            bundle.getPersonalizationReasoning().add(PersonalizationReasoningItem.builder()
                                    .factor("PREFERRED_BRAND")
                                    .productId(p.getId())
                                    .productName(p.getName())
                                    .explanation("Matches your preferred brand " + p.getBrand())
                                    .scoreContribution(10.0)
                                    .build());
                        }
                    }
                }

                if (discRes.getContent().size() >= 2) {
                    bundle.getSuggestedActions().add(AssistantAction.builder()
                            .type("COMPARE")
                            .label("Compare Top 2")
                            .description("Run side-by-side comparison matrix")
                            .payload(Map.of("productIds", List.of(
                                    discRes.getContent().get(0).getId().toString(),
                                    discRes.getContent().get(1).getId().toString()
                            )))
                            .build());
                }
            } else {
                bundle.getUnknownOrInsufficientData().add("No catalog products matched this specific search query");
            }
        } catch (Exception e) {
            log.warn("Discovery query failed during assistant orchestration: {}", e.getMessage());
            bundle.getUnknownOrInsufficientData().add("Product catalog search temporarily unavailable");
        }
    }

    private void populateComparisonEvidence(
            AssistantEvidenceBundle bundle,
            String query,
            UUID activeProductId,
            UserEntity user) {

        List<ProductEntity> matched = findCandidateProductsForQuery(query, activeProductId);
        if (matched.size() < 2) {
            bundle.getUnknownOrInsufficientData().add("Comparison requires at least 2 distinct products. Please name 2 products to compare");
            return;
        }

        List<UUID> productIds = matched.stream().map(ProductEntity::getId).limit(4).collect(Collectors.toList());
        try {
            RecommendationCompareRequest req = new RecommendationCompareRequest(productIds, "BEST_OVERALL");
            RecommendationResponse compRes = recommendationService.compareAndRecommend(req, user.getId());

            for (ProductEntity p : matched) {
                Map<String, Object> card = new HashMap<>();
                card.put("id", p.getId().toString());
                card.put("name", p.getName());
                card.put("brand", p.getBrand());
                card.put("category", p.getCategory());
                bundle.getGroundedProducts().add(card);
            }

            if (compRes != null && compRes.getScores() != null) {
                for (ProductScore ps : compRes.getScores()) {
                    bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                            .productId(ps.getProductId())
                            .productName(ps.getProductName())
                            .factType("COMPARISON_SCORE")
                            .description(String.format("Comparison Score: %.1f/100 (Badge: %s)", ps.getOverallScore(), ps.getRecommendationBadge()))
                            .factualValue(ps.getOverallScore())
                            .verified(true)
                            .confidence(0.90)
                            .build());
                }
            }

            bundle.getSuggestedActions().add(AssistantAction.builder()
                    .type("VIEW_COMPARISON")
                    .label("Open Full Comparison Matrix")
                    .description("View comprehensive attribute differences and radar scores")
                    .payload(Map.of("productIds", productIds.stream().map(UUID::toString).collect(Collectors.toList())))
                    .build());

        } catch (Exception e) {
            log.warn("Comparison execution failed during assistant orchestration: {}", e.getMessage());
            bundle.getUnknownOrInsufficientData().add("Comparison matrix calculation encountered an issue");
        }
    }

    private void populatePriceAnalysisEvidence(
            AssistantEvidenceBundle bundle,
            String query,
            UUID activeProductId) {

        ProductEntity product = null;
        if (activeProductId != null) {
            product = productRepository.findById(activeProductId).orElse(null);
        }
        if (product == null) {
            List<ProductEntity> list = findCandidateProductsForQuery(query, null);
            if (!list.isEmpty()) {
                product = list.get(0);
            }
        }

        if (product == null) {
            bundle.getUnknownOrInsufficientData().add("Could not identify which specific product to analyze. Please specify a product name");
            return;
        }

        Map<String, Object> card = new HashMap<>();
        card.put("id", product.getId().toString());
        card.put("name", product.getName());
        card.put("brand", product.getBrand());
        card.put("category", product.getCategory());
        bundle.getGroundedProducts().add(card);

        try {
            ProductAnalyticsResponseDTO analytics = priceAnalyticsService.getProductAnalytics(product.getId());
            if (analytics != null) {
                bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .factType("HISTORICAL_PRICE")
                        .description(String.format("Current: $%s | Historical Low: $%s | Historical High: $%s",
                                analytics.getCurrentPrice() != null ? analytics.getCurrentPrice() : "N/A",
                                analytics.getHistoricalMin() != null ? analytics.getHistoricalMin() : "N/A",
                                analytics.getHistoricalMax() != null ? analytics.getHistoricalMax() : "N/A"))
                        .factualValue(analytics.getCurrentPrice())
                        .verified(true)
                        .confidence(0.95)
                        .build());

                bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .factType("DEAL_QUALITY")
                        .description(String.format("Deal Rating: %s (Signal: %s, Trend: %s)",
                                analytics.getDealQuality() != null ? analytics.getDealQuality() : "UNKNOWN",
                                analytics.getPurchaseSignal() != null ? analytics.getPurchaseSignal() : "UNKNOWN",
                                analytics.getTrend() != null ? analytics.getTrend() : "STABLE"))
                        .factualValue(analytics.getDealQuality() != null ? analytics.getDealQuality().name() : null)
                        .verified(true)
                        .confidence(0.90)
                        .build());

                // Suggest action to set watchlist target
                if (analytics.getHistoricalMin() != null) {
                    bundle.getSuggestedActions().add(AssistantAction.builder()
                            .type("SET_TARGET_PRICE")
                            .label("Set Alert at Historical Low ($" + analytics.getHistoricalMin() + ")")
                            .description("Notify me when price drops to or below its lowest recorded level")
                            .payload(Map.of("productId", product.getId().toString(), "targetPrice", analytics.getHistoricalMin()))
                            .build());
                }
            } else {
                bundle.getUnknownOrInsufficientData().add("Historical price analysis has insufficient data points for " + product.getName());
            }
        } catch (Exception e) {
            log.warn("Price analytics query failed for {}: {}", product.getId(), e.getMessage());
            bundle.getUnknownOrInsufficientData().add("Price history analytics unavailable for this product");
        }
    }

    private void populateRecommendationEvidence(
            AssistantEvidenceBundle bundle,
            UUID userId,
            UserShoppingPreferenceDTO preferences) {

        try {
            RecommendationResponse recRes = recommendationService.getPersonalizedRecommendations(userId, 4);
            if (recRes != null && recRes.getRecommendedProducts() != null && !recRes.getRecommendedProducts().isEmpty()) {
                for (var p : recRes.getRecommendedProducts()) {
                    Map<String, Object> card = new HashMap<>();
                    card.put("id", p.getId().toString());
                    card.put("name", p.getName());
                    card.put("brand", p.getBrand());
                    card.put("category", p.getCategory());
                    card.put("recommendationScore", p.getRecommendationScore());
                    bundle.getGroundedProducts().add(card);

                    bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                            .productId(p.getId())
                            .productName(p.getName())
                            .factType("RECOMMENDATION_SCORE")
                            .description(String.format("Personalized Score: %.1f/100", p.getRecommendationScore() != null ? p.getRecommendationScore() : 80.0))
                            .factualValue(p.getRecommendationScore())
                            .verified(true)
                            .confidence(0.90)
                            .build());
                }

                bundle.getSuggestedActions().add(AssistantAction.builder()
                        .type("EXPLORE_RECOMMENDATIONS")
                        .label("View All Personalized Picks")
                        .description("See personalized products scored against your shopping preferences")
                        .payload(Map.of())
                        .build());
            } else {
                bundle.getUnknownOrInsufficientData().add("No recommendations available for your current preferences");
            }
        } catch (Exception e) {
            log.warn("Personalized recommendation failed during assistant orchestration: {}", e.getMessage());
            bundle.getUnknownOrInsufficientData().add("Personalization engine temporarily unavailable");
        }
    }

    private void populateWatchlistEvidence(
            AssistantEvidenceBundle bundle,
            UserEntity user) {

        try {
            List<WatchlistResponseDTO> watchlists = priceWatchlistService.getAllWatchlists(user.getEmail());
            if (watchlists != null && !watchlists.isEmpty()) {
                for (WatchlistResponseDTO w : watchlists.stream().limit(5).collect(Collectors.toList())) {
                    bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                            .productId(w.getProductId())
                            .productName(w.getProductName())
                            .factType("WATCHLIST")
                            .description(String.format("Target: $%s | Current Best: $%s", w.getTargetPrice(), w.getCurrentBestPrice()))
                            .factualValue(w.getTargetPrice())
                            .verified(true)
                            .confidence(1.0)
                            .build());
                }
            } else {
                bundle.getUnknownOrInsufficientData().add("You do not have any active price watchlists");
            }

            bundle.getSuggestedActions().add(AssistantAction.builder()
                    .type("VIEW_WATCHLIST")
                    .label("Manage Watchlists & Alerts")
                    .description("Open watchlist dashboard")
                    .payload(Map.of())
                    .build());
        } catch (Exception e) {
            log.warn("Watchlist retrieval failed during assistant orchestration: {}", e.getMessage());
            bundle.getUnknownOrInsufficientData().add("Could not retrieve active watchlists");
        }
    }

    private void populatePreferenceEvidence(
            AssistantEvidenceBundle bundle,
            UserShoppingPreferenceDTO preferences) {

        if (preferences == null) {
            bundle.getUnknownOrInsufficientData().add("No custom shopping preferences configured");
            return;
        }

        bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                .factType("USER_PREFERENCE")
                .description(String.format("Budget: %s | Deal Sensitivity: %s | Availability: %s",
                        preferences.getMaxBudget() != null ? "Up to $" + preferences.getMaxBudget() : "Uncapped",
                        preferences.getDealSensitivity(),
                        preferences.getAvailabilityPreference()))
                .verified(true)
                .confidence(1.0)
                .build());

        bundle.getSuggestedActions().add(AssistantAction.builder()
                .type("UPDATE_PREFERENCES")
                .label("Adjust Shopping Preferences")
                .description("Update budget, categories, or brand preferences")
                .payload(Map.of())
                .build());
    }

    private void populateGeneralEvidence(AssistantEvidenceBundle bundle) {
        bundle.getSuggestedActions().add(AssistantAction.builder()
                .type("SUGGESTED_PROMPT")
                .label("Find best smartphone deals")
                .description("Search top deal-rated smartphones")
                .payload(Map.of("query", "smartphone deals"))
                .build());
        bundle.getSuggestedActions().add(AssistantAction.builder()
                .type("SUGGESTED_PROMPT")
                .label("What are my preferences?")
                .description("Check your active budget and preferred brands")
                .payload(Map.of("query", "what are my preferences"))
                .build());
    }

    private String generateAssistantText(
            String userQuery,
            UUID conversationId,
            AssistantIntent intent,
            AssistantEvidenceBundle bundle,
            UserShoppingPreferenceDTO preferences) {

        // Try LLM augmentation via AiClient if available
        if (aiClient != null && aiClient.isAvailable()) {
            try {
                Map<String, Object> aiReq = new HashMap<>();
                aiReq.put("message", promptProtector.wrapInSafeBoundary(userQuery));
                aiReq.put("conversationId", conversationId.toString());
                aiReq.put("intent", intent.name());
                aiReq.put("evidenceBundle", bundle);

                Map<String, Object> aiRes = aiClient.chat(aiReq, null);
                if (aiRes != null && aiRes.containsKey("response")) {
                    String candidate = (String) aiRes.get("response");
                    if (promptProtector.isResponseGrounded(candidate, bundle)) {
                        log.debug("AI response accepted and grounded | conv_id={}", conversationId);
                        return candidate;
                    } else {
                        log.warn("AI response rejected due to ungrounded or fabricated claims | conv_id={}", conversationId);
                    }
                }
            } catch (Exception e) {
                log.info("AI service chat call unavailable or timed out, executing deterministic fallback: {}", e.getMessage());
            }
        }

        // Safe deterministic fallback
        return fallbackGenerator.generateFallbackResponse(intent, bundle, preferences);
    }

    private AssistantResponseDTO buildResponseDTO(
            UUID conversationId,
            UUID messageId,
            AssistantIntent intent,
            String responseText,
            AssistantEvidenceBundle bundle) {

        List<String> suggestedPrompts = bundle.getSuggestedActions().stream()
                .map(AssistantAction::getLabel)
                .collect(Collectors.toList());

        return AssistantResponseDTO.builder()
                .response(responseText)
                .conversationId(conversationId)
                .messageId(messageId)
                .intent(intent)
                .evidenceBundle(bundle)
                .products(bundle.getGroundedProducts())
                .suggestedPrompts(suggestedPrompts)
                .suggestedActions(bundle.getSuggestedActions())
                .build();
    }

    private List<ProductEntity> findCandidateProductsForQuery(String query, UUID activeProductId) {
        List<ProductEntity> results = new ArrayList<>();
        if (activeProductId != null) {
            productRepository.findById(activeProductId).ifPresent(results::add);
        }

        if (query != null && !query.trim().isEmpty()) {
            String[] tokens = query.replaceAll("[^a-zA-Z0-9 ]", "").split("\\s+");
            for (String token : tokens) {
                if (token.length() > 2 && !token.equalsIgnoreCase("and") && !token.equalsIgnoreCase("the")
                        && !token.equalsIgnoreCase("compare") && !token.equalsIgnoreCase("between")) {
                    List<ProductEntity> matches = productRepository
                            .findByNameContainingIgnoreCaseOrBrandContainingIgnoreCaseOrCategoryContainingIgnoreCase(
                                    token, token, token, PageRequest.of(0, 3)
                            ).getContent();
                    for (ProductEntity p : matches) {
                        if (results.stream().noneMatch(r -> r.getId().equals(p.getId()))) {
                            results.add(p);
                        }
                    }
                }
            }
        }
        return results;
    }

    private UserEntity getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private String serializeBundle(AssistantEvidenceBundle bundle) {
        if (bundle == null) return null;
        try {
            return objectMapper.writeValueAsString(bundle);
        } catch (Exception e) {
            log.error("Failed to serialize evidence bundle: {}", e.getMessage());
            return null;
        }
    }

    private AssistantEvidenceBundle deserializeBundle(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, AssistantEvidenceBundle.class);
        } catch (Exception e) {
            log.error("Failed to deserialize evidence bundle: {}", e.getMessage());
            return null;
        }
    }

    private String serializePayload(Object payload) {
        if (payload == null) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload: {}", e.getMessage());
            return null;
        }
    }

    private Object deserializePayload(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return json;
        }
    }
}
