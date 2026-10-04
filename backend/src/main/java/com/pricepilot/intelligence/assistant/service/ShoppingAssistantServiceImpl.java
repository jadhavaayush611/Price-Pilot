package com.pricepilot.intelligence.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.ai.AiClient;
import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.currency.CurrencyCode;
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
import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.intelligence.discovery.interpretation.QueryInterpreter;
import com.pricepilot.intelligence.discovery.service.SearchDiscoveryService;
import com.pricepilot.intelligence.discovery.specification.DiscoverySpecifications;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import com.pricepilot.intelligence.recommendation.RecommendationService;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.intelligence.recommendation.dto.RecommendationCompareRequest;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.ProductPriceEntity;
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

import java.util.regex.Pattern;

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
    private final ProductService productService;
    private final SearchDiscoveryService searchDiscoveryService;
    private final QueryInterpreter queryInterpreter;
    private final ComparisonService comparisonService;
    private final PriceAnalyticsService priceAnalyticsService;
    private final PriceWatchlistService priceWatchlistService;
    private final PriceAlertService priceAlertService;
    private final UserShoppingPreferenceService preferenceService;
    private final RecommendationService recommendationService;
    private final ShoppingIntentClassifier intentClassifier;
    private final PromptInjectionProtector promptProtector;
    private final DeterministicShoppingAssistantFallback fallbackGenerator;
    private final com.pricepilot.intelligence.assistant.matching.ExactProductMatchEvaluator exactProductMatchEvaluator;
    private final com.pricepilot.currency.CurrencyConversionService currencyConversionService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public ShoppingAssistantServiceImpl(
            AssistantConversationRepository conversationRepository,
            AssistantMessageRepository messageRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            ProductPriceRepository productPriceRepository,
            ProductService productService,
            SearchDiscoveryService searchDiscoveryService,
            QueryInterpreter queryInterpreter,
            ComparisonService comparisonService,
            PriceAnalyticsService priceAnalyticsService,
            PriceWatchlistService priceWatchlistService,
            PriceAlertService priceAlertService,
            UserShoppingPreferenceService preferenceService,
            RecommendationService recommendationService,
            ShoppingIntentClassifier intentClassifier,
            PromptInjectionProtector promptProtector,
            DeterministicShoppingAssistantFallback fallbackGenerator,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.pricepilot.intelligence.assistant.matching.ExactProductMatchEvaluator exactProductMatchEvaluator,
            com.pricepilot.currency.CurrencyConversionService currencyConversionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AiClient aiClient) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productPriceRepository = productPriceRepository;
        this.productService = productService;
        this.searchDiscoveryService = searchDiscoveryService;
        this.queryInterpreter = queryInterpreter;
        this.comparisonService = comparisonService;
        this.priceAnalyticsService = priceAnalyticsService;
        this.priceWatchlistService = priceWatchlistService;
        this.priceAlertService = priceAlertService;
        this.preferenceService = preferenceService;
        this.recommendationService = recommendationService;
        this.intentClassifier = intentClassifier;
        this.promptProtector = promptProtector;
        this.fallbackGenerator = fallbackGenerator;
        this.exactProductMatchEvaluator = exactProductMatchEvaluator != null ? exactProductMatchEvaluator
                : new com.pricepilot.intelligence.assistant.matching.ExactProductMatchEvaluator(new com.pricepilot.intelligence.discovery.normalization.QueryNormalizer());
        this.currencyConversionService = currencyConversionService != null ? currencyConversionService : new com.pricepilot.currency.CurrencyConversionServiceImpl(
                new com.pricepilot.currency.ConfiguredCurrencyRateProvider(new com.pricepilot.currency.CurrencyProperties()),
                new com.pricepilot.currency.CurrencyProperties()
        );
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
            List<AssistantMessageEntity> msgEntities = messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId());
            if (!msgEntities.isEmpty()) {
                AssistantMessageEntity userMsg = msgEntities.get(0);
                messages.add(AssistantMessageDTO.fromEntity(userMsg, null, null));
            }
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
                .createdAt(LocalDateTime.now())
                .build();
        userMessage = messageRepository.save(userMessage);
        conversation.getMessages().add(userMessage);

        // 2. Classify intent
        AssistantIntent intent = intentClassifier.classifyIntent(sanitizedContent);

        // 3. Extract user preferences & signals
        UserShoppingPreferenceDTO preferences = null;
        try {
            preferences = preferenceService.getPreferences(userId);
        } catch (Exception e) {
            log.warn("Unable to fetch preferences for user {}: {}", userId, e.getMessage());
        }

        CurrencyCode displayCurrency = (preferences != null && preferences.getCurrency() != null)
                ? preferences.getCurrency()
                : currencyConversionService.getDefaultDisplayCurrency();

        // 4. Build grounded evidence bundle (Single unified resolution pipeline)
        AssistantEvidenceBundle bundle = buildEvidenceBundle(
                intent, sanitizedContent, request.getActiveProductId(), conversation.getId(), user, preferences, displayCurrency);

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
                .createdAt(LocalDateTime.now())
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
        return buildResponseDTO(conversation.getId(), assistantMessage.getId(), intent, responseText, bundle, displayCurrency);
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

    private UUID resolveActiveProductIdFromHistory(UUID conversationId) {
        if (conversationId == null) return null;
        List<AssistantMessageEntity> history = conversationRepository.findById(conversationId)
                .map(AssistantConversationEntity::getMessages)
                .filter(m -> !m.isEmpty())
                .orElseGet(() -> messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId));

        for (int i = history.size() - 1; i >= 0; i--) {
            AssistantMessageEntity msg = history.get(i);
            if (msg.getRole() == MessageRole.ASSISTANT) {
                if (msg.getPayload() != null && !msg.getPayload().trim().isEmpty()) {
                    try {
                        List<Map<String, Object>> prods = objectMapper.readValue(msg.getPayload(), new TypeReference<List<Map<String, Object>>>() {});
                        if (prods != null && !prods.isEmpty()) {
                            Map<String, Object> first = prods.get(0);
                            Object idObj = first.get("productId");
                            if (idObj == null) idObj = first.get("id");
                            if (idObj != null) {
                                return UUID.fromString(idObj.toString());
                            }
                        }
                    } catch (Exception ignored) {}
                }
                AssistantEvidenceBundle bundle = deserializeBundle(msg.getEvidenceBundle());
                if (bundle != null && bundle.getGroundedProducts() != null && !bundle.getGroundedProducts().isEmpty()) {
                    Map<String, Object> first = bundle.getGroundedProducts().get(0);
                    Object idObj = first.get("productId");
                    if (idObj == null) idObj = first.get("id");
                    if (idObj != null) {
                        try {
                            return UUID.fromString(idObj.toString());
                        } catch (Exception ignored) {}
                    }
                }
            } else if (msg.getRole() == MessageRole.USER && msg.getContent() != null) {
                ProductEntity resolved = resolveProductFromCatalog(msg.getContent());
                if (resolved != null) {
                    return resolved.getId();
                }
            }
        }
        return null;
    }

    private ProductEntity resolveProductFromCatalog(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<ProductEntity> allProducts = productRepository.findAll();
        // Sort by product name length descending so "Apple iPhone 15 Pro" matches before "Apple iPhone 15"
        allProducts.sort((a, b) -> Integer.compare(b.getName().length(), a.getName().length()));

        // 1. Exact full name match
        for (ProductEntity p : allProducts) {
            String lowerName = p.getName().toLowerCase(Locale.ROOT);
            if (Pattern.compile("\\b" + Pattern.quote(lowerName) + "\\b").matcher(lowerQuery).find()) {
                return p;
            }
            if (p.getBrand() != null && lowerName.startsWith(p.getBrand().toLowerCase(Locale.ROOT) + " ")) {
                String withoutBrand = lowerName.substring(p.getBrand().length() + 1).trim();
                if (!withoutBrand.isEmpty() && Pattern.compile("\\b" + Pattern.quote(withoutBrand) + "\\b").matcher(lowerQuery).find()) {
                    return p;
                }
            }
        }

        // 2. Token coverage (e.g. "iphone 15" without full brand)
        for (ProductEntity p : allProducts) {
            String namePart = p.getName().toLowerCase(Locale.ROOT);
            if (p.getBrand() != null && namePart.startsWith(p.getBrand().toLowerCase(Locale.ROOT) + " ")) {
                namePart = namePart.substring(p.getBrand().length() + 1).trim();
            }
            String[] nameTokens = namePart.split("\\s+");
            boolean allTokensPresent = true;
            int matchedTokens = 0;
            for (String t : nameTokens) {
                if (t.length() < 2) continue;
                if (Pattern.compile("\\b" + Pattern.quote(t) + "\\b").matcher(lowerQuery).find()) {
                    matchedTokens++;
                } else {
                    allTokensPresent = false;
                    break;
                }
            }
            if (allTokensPresent && matchedTokens >= 2) {
                return p;
            }
        }

        return null;
    }

    private boolean isExplicitProductMention(String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) return false;
        String clean = targetName.replaceAll("(?i)\\b(it|this|that|these|those|the|product|item|price|deal|options|choice|one|now|today|catalog|me|us)\\b|[?!.,]", "").trim();
        return !clean.isEmpty();
    }

    private ProductEntity resolveProductForQuery(String query, UUID activeProductId, UUID conversationId) {
        // 1. Check if the query explicitly mentions a product name from the catalog
        ProductEntity fromCatalog = resolveProductFromCatalog(query);
        if (fromCatalog != null) {
            return fromCatalog;
        }

        // 1b. If the user explicitly named an uncataloged target (e.g. "iPhone 16"), do NOT contaminate with history
        String targetName = extractTargetProductName(query);
        if (targetName != null && isExplicitProductMention(targetName)) {
            return null;
        }

        // 2. If explicit activeProductId was provided in request and no explicit new product in query
        if (activeProductId != null) {
            Optional<ProductEntity> explicit = productRepository.findById(activeProductId);
            if (explicit.isPresent()) {
                return explicit.get();
            }
        }

        // 3. If no explicit product in current query (e.g. "Is now a good time to buy it?"), check multi-turn conversation history
        if (conversationId != null) {
            UUID historyProductId = resolveActiveProductIdFromHistory(conversationId);
            if (historyProductId != null) {
                Optional<ProductEntity> fromHistory = productRepository.findById(historyProductId);
                if (fromHistory.isPresent()) {
                    return fromHistory.get();
                }
            }
        }

        return null;
    }

    private String extractTargetProductName(String query) {
        if (query == null) return null;
        String clean = query.replaceAll("(?i)\\b(should i buy|is now a good time to buy|is it worth buying|what about|tell me about|how is|price of|the|now|it|is|a|an)\\b|[?!.]", "").trim();
        return clean.isEmpty() ? null : clean;
    }

    private AssistantEvidenceBundle buildEvidenceBundle(
            AssistantIntent intent,
            String query,
            UUID activeProductId,
            UUID conversationId,
            UserEntity user,
            UserShoppingPreferenceDTO preferences,
            CurrencyCode displayCurrency) {

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
            case DISCOVERY -> populateDiscoveryEvidence(bundle, query, preferences, displayCurrency);
            case COMPARISON -> populateComparisonEvidence(bundle, query, activeProductId, user, displayCurrency);
            case PRICE_ANALYSIS -> populatePriceAnalysisEvidence(bundle, query, activeProductId, conversationId, displayCurrency);
            case RECOMMENDATION -> populateRecommendationEvidence(bundle, query, user.getId(), preferences, displayCurrency);
            case WATCHLIST_ACTION -> populateWatchlistEvidence(bundle, user, displayCurrency);
            case PREFERENCE_QUERY -> populatePreferenceEvidence(bundle, preferences, displayCurrency);
            default -> populateGeneralEvidence(bundle);
        }

        return bundle;
    }

    private void populateDiscoveryEvidence(
            AssistantEvidenceBundle bundle,
            String query,
            UserShoppingPreferenceDTO preferences,
            CurrencyCode displayCurrency) {

        InterpretedQuery interpreted = queryInterpreter.interpret(query, displayCurrency);
        String searchQuery = (interpreted.getSearchTokens() != null && !interpreted.getSearchTokens().isEmpty())
                ? String.join(" ", interpreted.getSearchTokens())
                : (interpreted.getCleanSearchTerms() != null ? interpreted.getCleanSearchTerms().trim() : "");

        BigDecimal maxPrice = interpreted.getMaxPrice();
        if (maxPrice == null && preferences != null && preferences.getMaxBudget() != null) {
            CurrencyCode prefCur = preferences.getCurrency() != null ? preferences.getCurrency() : displayCurrency;
            maxPrice = currencyConversionService.convertToCanonical(preferences.getMaxBudget(), prefCur);
        }
        BigDecimal minPrice = interpreted.getMinPrice();
        String category = interpreted.getDetectedCategory();
        String brand = interpreted.getDetectedBrand();

        DiscoverySearchRequestDTO req = DiscoverySearchRequestDTO.builder()
                .query(searchQuery)
                .category(category)
                .brand(brand)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .page(0)
                .size(5)
                .build();

        try {
            DiscoverySearchResponseDTO discRes = searchDiscoveryService.searchAndDiscover(req);
            List<DiscoveryProductDTO> rawCandidates = (discRes != null && discRes.getContent() != null)
                    ? new ArrayList<>(discRes.getContent()) : new ArrayList<>();

            // If an exact-product query yielded zero initial candidates (e.g., exact model generation not present in catalog),
            // retrieve nearby candidates from the product family or brand/category so close matches can be presented
            if (rawCandidates.isEmpty() && exactProductMatchEvaluator.isExactProductQuery(query)) {
                String family = exactProductMatchEvaluator.extractProductFamily(query);
                String queryBrand = exactProductMatchEvaluator.extractSpecifiedBrand(query);
                String relaxedQuery = family != null ? family : (queryBrand != null ? queryBrand : (brand != null ? brand : (category != null ? category : "")));
                if (!relaxedQuery.isBlank()) {
                    DiscoverySearchRequestDTO relaxedReq = DiscoverySearchRequestDTO.builder()
                            .query(relaxedQuery)
                            .category(category)
                            .brand(queryBrand != null ? queryBrand : brand)
                            .minPrice(minPrice)
                            .maxPrice(maxPrice)
                            .page(0)
                            .size(5)
                            .build();
                    try {
                        DiscoverySearchResponseDTO relaxedRes = searchDiscoveryService.searchAndDiscover(relaxedReq);
                        if (relaxedRes != null && relaxedRes.getContent() != null) {
                            rawCandidates.addAll(relaxedRes.getContent());
                        }
                    } catch (Exception ignored) {}
                }
            }

            com.pricepilot.intelligence.assistant.matching.ExactProductMatchEvaluator.EvaluationResult evalResult =
                    exactProductMatchEvaluator.evaluateCandidates(query, rawCandidates, null);

            bundle.setMatchClassification(evalResult.getClassification());
            bundle.setRequestedEntity(evalResult.getRequestedEntity());

            List<DiscoveryProductDTO> displayProducts = new ArrayList<>();
            if (evalResult.getClassification() == AssistantMatchClassification.EXACT_MATCH) {
                displayProducts.addAll(evalResult.getExactMatches());
            } else if (evalResult.getClassification() == AssistantMatchClassification.CLOSE_MATCHES) {
                displayProducts.addAll(evalResult.getCloseMatches());
            } else if (evalResult.getClassification() == AssistantMatchClassification.CATEGORY_RESULTS) {
                displayProducts.addAll(rawCandidates);
            }

            if (!displayProducts.isEmpty()) {
                for (DiscoveryProductDTO p : displayProducts) {
                    BigDecimal currentPriceDisplay = currencyConversionService.convertFromCanonical(p.getCurrentBestPrice(), displayCurrency);
                    BigDecimal origPriceDisplay = currencyConversionService.convertFromCanonical(p.getOriginalPrice(), displayCurrency);

                    Map<String, Object> card = new HashMap<>();
                    card.put("productId", p.getId().toString());
                    card.put("id", p.getId().toString());
                    card.put("productName", p.getName());
                    card.put("name", p.getName());
                    card.put("brand", p.getBrand());
                    card.put("category", p.getCategory());
                    card.put("description", p.getDescription());
                    card.put("imageUrl", p.getImageUrl());
                    card.put("currentPrice", currentPriceDisplay != null ? currentPriceDisplay.doubleValue() : null);
                    card.put("price", currentPriceDisplay != null ? currentPriceDisplay.doubleValue() : null);
                    card.put("originalPrice", origPriceDisplay != null ? origPriceDisplay.doubleValue() : null);
                    card.put("discountPercentage", p.getDiscountPercentage() != null ? p.getDiscountPercentage().doubleValue() : 0.0);
                    card.put("discount", p.getDiscountPercentage() != null ? p.getDiscountPercentage().doubleValue() : 0.0);
                    card.put("currency", displayCurrency.name());
                    card.put("currencySymbol", displayCurrency.getSymbol());
                    card.put("dealQuality", p.getDealQuality() != null ? p.getDealQuality().name() : (p.getPrices() != null && !p.getPrices().isEmpty() ? "AVAILABLE" : "UNKNOWN"));
                    card.put("rating", p.getRating());
                    card.put("inStock", p.getInStock());
                    bundle.getGroundedProducts().add(card);

                    String formattedPrice = currentPriceDisplay != null ? displayCurrency.getSymbol() + formatMoney(currentPriceDisplay, displayCurrency) : "N/A";
                    bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                            .productId(p.getId())
                            .productName(p.getName())
                            .factType("PRICE")
                            .description(String.format("Current price: %s (Discount: %s%%)", 
                                    formattedPrice, 
                                    p.getDiscountPercentage() != null ? p.getDiscountPercentage() : "0"))
                            .factualValue(currentPriceDisplay)
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

                if (displayProducts.size() >= 2) {
                    String p1 = displayProducts.get(0).getId().toString();
                    String p2 = displayProducts.get(1).getId().toString();
                    bundle.getSuggestedActions().add(AssistantAction.builder()
                            .type("COMPARE")
                            .label("Compare Top 2")
                            .description("Run side-by-side comparison matrix")
                            .actionUrl("/compare?ids=" + p1 + "," + p2)
                            .payload(Map.of("productIds", List.of(p1, p2)))
                            .build());
                }
            } else {
                String limitMsg;
                if (evalResult.getClassification() == AssistantMatchClassification.NO_MATCH && evalResult.getRequestedEntity() != null) {
                    limitMsg = "No catalog products found for '" + evalResult.getRequestedEntity() + "'";
                } else {
                    limitMsg = "No catalog products found";
                    if (category != null) limitMsg += " in category '" + category + "'";
                    if (interpreted.getRawMaxPrice() != null) {
                        CurrencyCode queryCur = interpreted.getSourceCurrency() != null ? interpreted.getSourceCurrency() : displayCurrency;
                        limitMsg += " under " + queryCur.getSymbol() + formatMoney(interpreted.getRawMaxPrice(), queryCur);
                    } else if (maxPrice != null) {
                        BigDecimal displayBound = currencyConversionService.convertFromCanonical(maxPrice, displayCurrency);
                        limitMsg += " under " + displayCurrency.getSymbol() + formatMoney(displayBound, displayCurrency);
                    }
                }
                bundle.getUnknownOrInsufficientData().add(limitMsg);
            }
        } catch (Exception e) {
            log.warn("Discovery query failed during assistant orchestration: {}", e.getMessage());
            bundle.setMatchClassification(AssistantMatchClassification.NO_MATCH);
            bundle.getUnknownOrInsufficientData().add("Product catalog search temporarily unavailable");
        }
    }

    private void populateComparisonEvidence(
            AssistantEvidenceBundle bundle,
            String query,
            UUID activeProductId,
            UserEntity user,
            CurrencyCode displayCurrency) {

        InterpretedQuery interpreted = queryInterpreter.interpret(query, displayCurrency);
        List<ProductEntity> candidates = new ArrayList<>();

        // 1. Check if specific distinct products are mentioned in query (e.g. "Compare iPhone 15 Pro and Galaxy S24 Ultra")
        List<ProductEntity> namedProducts = findExplicitCatalogProductsForQuery(query);
        if (activeProductId != null) {
            productRepository.findById(activeProductId).ifPresent(p -> {
                if (namedProducts.stream().noneMatch(n -> n.getId().equals(p.getId()))) {
                    namedProducts.add(p);
                }
            });
        }

        // Validate named products against category and price bounds if specified in query
        List<ProductEntity> validNamed = new ArrayList<>();
        for (ProductEntity p : namedProducts) {
            if (interpreted.getDetectedCategory() != null && !interpreted.getDetectedCategory().equalsIgnoreCase(p.getCategory())) {
                continue;
            }
            if (interpreted.getMaxPrice() != null) {
                List<ProductPriceEntity> pPrices = productPriceRepository.findPricesWithSellersByProductIds(List.of(p.getId()));
                BigDecimal minP = pPrices.stream().map(ProductPriceEntity::getCurrentPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
                if (minP != null && minP.compareTo(interpreted.getMaxPrice()) > 0) {
                    continue;
                }
            }
            validNamed.add(p);
        }

        if (validNamed.size() >= 2) {
            candidates.addAll(validNamed);
        } else {
            // 2. Otherwise run discovery with category, brand, budget constraints
            String cleanQuery = (interpreted.getSearchTokens() != null && !interpreted.getSearchTokens().isEmpty())
                    ? String.join(" ", interpreted.getSearchTokens())
                    : (interpreted.getCleanSearchTerms() != null ? interpreted.getCleanSearchTerms().trim() : "");
            DiscoverySearchRequestDTO discReq = DiscoverySearchRequestDTO.builder()
                    .query(cleanQuery)
                    .category(interpreted.getDetectedCategory())
                    .brand(interpreted.getDetectedBrand())
                    .minPrice(interpreted.getMinPrice())
                    .maxPrice(interpreted.getMaxPrice())
                    .page(0)
                    .size(4)
                    .build();

            try {
                DiscoverySearchResponseDTO discRes = searchDiscoveryService.searchAndDiscover(discReq);
                if (discRes != null && discRes.getContent() != null) {
                    for (DiscoveryProductDTO dp : discRes.getContent()) {
                        productRepository.findById(dp.getId()).ifPresent(p -> {
                            if (candidates.stream().noneMatch(c -> c.getId().equals(p.getId()))) {
                                candidates.add(p);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                log.warn("Discovery search for comparison failed: {}", e.getMessage());
            }

            // If candidates < 2 and category is known, check database for candidates in same category & budget
            if (candidates.size() < 2 && interpreted.getDetectedCategory() != null) {
                var spec = DiscoverySpecifications.buildDiscoverySpec(
                        null,
                        interpreted.getDetectedCategory(),
                        interpreted.getDetectedBrand(),
                        interpreted.getMinPrice(),
                        interpreted.getMaxPrice(),
                        null,
                        null
                );
                List<ProductEntity> catCandidates = productRepository.findAll(spec, PageRequest.of(0, 4)).getContent();
                for (ProductEntity p : catCandidates) {
                    if (candidates.stream().noneMatch(c -> c.getId().equals(p.getId()))) {
                        candidates.add(p);
                    }
                    if (candidates.size() >= 4) break;
                }
            }
        }

        if (candidates.size() < 2) {
            String note = "Comparison requires at least 2 distinct products meeting your criteria.";
            if (interpreted.getDetectedCategory() != null) note += " (Category: " + interpreted.getDetectedCategory() + ")";
            if (interpreted.getRawMaxPrice() != null) {
                CurrencyCode queryCur = interpreted.getSourceCurrency() != null ? interpreted.getSourceCurrency() : displayCurrency;
                note += " under " + queryCur.getSymbol() + formatMoney(interpreted.getRawMaxPrice(), queryCur);
            } else if (interpreted.getMaxPrice() != null) {
                BigDecimal displayBound = currencyConversionService.convertFromCanonical(interpreted.getMaxPrice(), displayCurrency);
                note += " under " + displayCurrency.getSymbol() + formatMoney(displayBound, displayCurrency);
            }
            bundle.getUnknownOrInsufficientData().add(note + ". Please broaden your budget or specify products to compare");
            return;
        }

        List<UUID> productIds = candidates.stream().map(ProductEntity::getId).limit(4).collect(Collectors.toList());
        try {
            RecommendationCompareRequest req = new RecommendationCompareRequest(productIds, "BEST_OVERALL");
            RecommendationResponse compRes = recommendationService.compareAndRecommend(req, user.getId());

            List<ProductPriceEntity> prices = productPriceRepository.findPricesWithSellersByProductIds(productIds);
            Map<UUID, List<ProductPriceEntity>> pricesByProductId = prices.stream()
                    .collect(Collectors.groupingBy(p -> p.getProduct().getId()));

            for (ProductEntity p : candidates.subList(0, productIds.size())) {
                List<ProductPriceEntity> pPrices = pricesByProductId.getOrDefault(p.getId(), List.of());
                BigDecimal currentPrice = pPrices.stream().map(ProductPriceEntity::getCurrentPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
                BigDecimal originalPrice = pPrices.stream().map(ProductPriceEntity::getOriginalPrice).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
                BigDecimal discount = pPrices.stream().map(ProductPriceEntity::getDiscountPercentage).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

                BigDecimal currentDisplay = currencyConversionService.convertFromCanonical(currentPrice, displayCurrency);
                BigDecimal origDisplay = currencyConversionService.convertFromCanonical(originalPrice, displayCurrency);

                Map<String, Object> card = new HashMap<>();
                card.put("productId", p.getId().toString());
                card.put("id", p.getId().toString());
                card.put("productName", p.getName());
                card.put("name", p.getName());
                card.put("brand", p.getBrand());
                card.put("category", p.getCategory());
                card.put("imageUrl", p.getImageUrl());
                card.put("currentPrice", currentDisplay != null ? currentDisplay.doubleValue() : null);
                card.put("price", currentDisplay != null ? currentDisplay.doubleValue() : null);
                card.put("originalPrice", origDisplay != null ? origDisplay.doubleValue() : null);
                card.put("discountPercentage", discount != null ? discount.doubleValue() : 0.0);
                card.put("discount", discount != null ? discount.doubleValue() : 0.0);
                card.put("currency", displayCurrency.name());
                card.put("currencySymbol", displayCurrency.getSymbol());
                card.put("dealQuality", !pPrices.isEmpty() ? "AVAILABLE" : "UNKNOWN");
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

            String idsParam = productIds.stream().map(UUID::toString).collect(Collectors.joining(","));
            bundle.getSuggestedActions().add(AssistantAction.builder()
                    .type("VIEW_COMPARISON")
                    .label("Open Full Comparison Matrix")
                    .description("View comprehensive attribute differences and radar scores")
                    .actionUrl("/compare?ids=" + idsParam)
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
            UUID activeProductId,
            UUID conversationId,
            CurrencyCode displayCurrency) {

        ProductEntity product = resolveProductForQuery(query, activeProductId, conversationId);

        if (product == null) {
            String target = extractTargetProductName(query);
            if (target != null && !target.isEmpty()) {
                bundle.getUnknownOrInsufficientData().add("No matching catalog product found for '" + target + "'");
            } else {
                bundle.getUnknownOrInsufficientData().add("Could not identify which specific product to analyze. Please specify a product name or select an active product");
            }
            return;
        }

        List<ProductPriceEntity> pPrices = productPriceRepository.findPricesWithSellersByProductIds(List.of(product.getId()));
        BigDecimal currentPrice = pPrices.stream().map(ProductPriceEntity::getCurrentPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
        BigDecimal originalPrice = pPrices.stream().map(ProductPriceEntity::getOriginalPrice).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
        BigDecimal discount = pPrices.stream().map(ProductPriceEntity::getDiscountPercentage).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        BigDecimal currentDisplay = currencyConversionService.convertFromCanonical(currentPrice, displayCurrency);
        BigDecimal origDisplay = currencyConversionService.convertFromCanonical(originalPrice, displayCurrency);

        Map<String, Object> card = new HashMap<>();
        card.put("productId", product.getId().toString());
        card.put("id", product.getId().toString());
        card.put("productName", product.getName());
        card.put("name", product.getName());
        card.put("brand", product.getBrand());
        card.put("category", product.getCategory());
        card.put("imageUrl", product.getImageUrl());
        card.put("currentPrice", currentDisplay != null ? currentDisplay.doubleValue() : null);
        card.put("price", currentDisplay != null ? currentDisplay.doubleValue() : null);
        card.put("originalPrice", origDisplay != null ? origDisplay.doubleValue() : null);
        card.put("discountPercentage", discount != null ? discount.doubleValue() : 0.0);
        card.put("discount", discount != null ? discount.doubleValue() : 0.0);
        card.put("currency", displayCurrency.name());
        card.put("currencySymbol", displayCurrency.getSymbol());
        card.put("dealQuality", !pPrices.isEmpty() ? "AVAILABLE" : "UNKNOWN");
        bundle.getGroundedProducts().add(card);

        try {
            ProductAnalyticsResponseDTO analytics = priceAnalyticsService.getProductAnalytics(product.getId());
            if (analytics != null) {
                BigDecimal currentDisplayVal = analytics.getCurrentPrice() != null
                        ? currencyConversionService.convertFromCanonical(analytics.getCurrentPrice(), displayCurrency)
                        : currentDisplay;
                BigDecimal histMinDisplay = currencyConversionService.convertFromCanonical(analytics.getHistoricalMin(), displayCurrency);
                BigDecimal histMaxDisplay = currencyConversionService.convertFromCanonical(analytics.getHistoricalMax(), displayCurrency);

                String curStr = currentDisplayVal != null ? displayCurrency.getSymbol() + formatMoney(currentDisplayVal, displayCurrency) : "N/A";
                String minStr = histMinDisplay != null ? displayCurrency.getSymbol() + formatMoney(histMinDisplay, displayCurrency) : "N/A";
                String maxStr = histMaxDisplay != null ? displayCurrency.getSymbol() + formatMoney(histMaxDisplay, displayCurrency) : "N/A";

                bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .factType("HISTORICAL_PRICE")
                        .description(String.format("Current: %s | Historical Low: %s | Historical High: %s", curStr, minStr, maxStr))
                        .factualValue(currentDisplayVal)
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

                if (histMinDisplay != null) {
                    bundle.getSuggestedActions().add(AssistantAction.builder()
                            .type("SET_TARGET_PRICE")
                            .label("Set Alert at Historical Low (" + displayCurrency.getSymbol() + formatMoney(histMinDisplay, displayCurrency) + ")")
                            .description("Notify me when price drops to or below its lowest recorded level")
                            .actionUrl("/watchlist")
                            .payload(Map.of("productId", product.getId().toString(), "targetPrice", histMinDisplay, "currency", displayCurrency.name()))
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
            String query,
            UUID userId,
            UserShoppingPreferenceDTO preferences,
            CurrencyCode displayCurrency) {

        boolean isTrending = query != null && (
                query.toLowerCase().contains("trending")
                || query.toLowerCase().contains("popular")
                || query.toLowerCase().contains("hot")
                || query.toLowerCase().contains("top products")
                || query.toLowerCase().contains("best sellers")
        );

        if (isTrending) {
            try {
                List<ProductResponseDTO> trending = productService.getTrendingProducts(4);
                if (trending != null && !trending.isEmpty()) {
                    for (ProductResponseDTO p : trending) {
                        BigDecimal bestPrice = null;
                        BigDecimal origPrice = null;
                        BigDecimal discount = BigDecimal.ZERO;
                        if (p.getPrices() != null && !p.getPrices().isEmpty()) {
                            bestPrice = p.getPrices().stream().map(ProductPriceResponseDTO::getCurrentPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
                            origPrice = p.getPrices().stream().map(ProductPriceResponseDTO::getOriginalPrice).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
                            discount = p.getPrices().stream().map(ProductPriceResponseDTO::getDiscountPercentage).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                        }

                        BigDecimal bestPriceDisplay = currencyConversionService.convertFromCanonical(bestPrice, displayCurrency);
                        BigDecimal origPriceDisplay = currencyConversionService.convertFromCanonical(origPrice, displayCurrency);

                        Map<String, Object> card = new HashMap<>();
                        card.put("productId", p.getId().toString());
                        card.put("id", p.getId().toString());
                        card.put("productName", p.getName());
                        card.put("name", p.getName());
                        card.put("brand", p.getBrand());
                        card.put("category", p.getCategory());
                        card.put("imageUrl", p.getImageUrl());
                        card.put("currentPrice", bestPriceDisplay != null ? bestPriceDisplay.doubleValue() : null);
                        card.put("price", bestPriceDisplay != null ? bestPriceDisplay.doubleValue() : null);
                        card.put("originalPrice", origPriceDisplay != null ? origPriceDisplay.doubleValue() : null);
                        card.put("discountPercentage", discount != null ? discount.doubleValue() : 0.0);
                        card.put("discount", discount != null ? discount.doubleValue() : 0.0);
                        card.put("currency", displayCurrency.name());
                        card.put("currencySymbol", displayCurrency.getSymbol());
                        card.put("dealQuality", (p.getPrices() != null && !p.getPrices().isEmpty()) ? "AVAILABLE" : "UNKNOWN");
                        bundle.getGroundedProducts().add(card);

                        String bestStr = bestPriceDisplay != null ? displayCurrency.getSymbol() + formatMoney(bestPriceDisplay, displayCurrency) : "N/A";
                        bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                                .productId(p.getId())
                                .productName(p.getName())
                                .factType("TRENDING_PRODUCT")
                                .description(String.format("Trending Product: %s | Best Price: %s", p.getName(), bestStr))
                                .factualValue(bestPriceDisplay)
                                .verified(true)
                                .confidence(0.95)
                                .build());
                    }
                } else {
                    bundle.getUnknownOrInsufficientData().add("No trending products currently recorded in the catalog");
                }
            } catch (Exception e) {
                log.warn("Trending products retrieval failed during assistant orchestration: {}", e.getMessage());
                bundle.getUnknownOrInsufficientData().add("Trending products discovery temporarily unavailable");
            }

            bundle.getSuggestedActions().add(AssistantAction.builder()
                    .type("EXPLORE_TRENDING")
                    .label("View All Trending Deals")
                    .description("Explore all trending products across categories")
                    .actionUrl("/trending")
                    .payload(Map.of())
                    .build());
            return;
        }

        try {
            RecommendationResponse recRes = recommendationService.getPersonalizedRecommendations(userId, 4);
            if (recRes != null && recRes.getRecommendedProducts() != null && !recRes.getRecommendedProducts().isEmpty()) {
                Map<UUID, ProductScore> scoresByProductId = (recRes.getScores() != null)
                        ? recRes.getScores().stream().collect(Collectors.toMap(ProductScore::getProductId, s -> s, (a, b) -> a))
                        : Map.of();

                for (com.pricepilot.product.dto.ProductResponseDTO p : recRes.getRecommendedProducts()) {
                    BigDecimal currentPrice = null;
                    BigDecimal originalPrice = null;
                    BigDecimal discount = BigDecimal.ZERO;
                    if (p.getPrices() != null && !p.getPrices().isEmpty()) {
                        currentPrice = p.getPrices().stream().map(ProductPriceResponseDTO::getCurrentPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
                        originalPrice = p.getPrices().stream().map(ProductPriceResponseDTO::getOriginalPrice).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
                        discount = p.getPrices().stream().map(ProductPriceResponseDTO::getDiscountPercentage).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                    }

                    BigDecimal currentDisplay = currencyConversionService.convertFromCanonical(currentPrice, displayCurrency);
                    BigDecimal origDisplay = currencyConversionService.convertFromCanonical(originalPrice, displayCurrency);

                    ProductScore score = scoresByProductId.get(p.getId());
                    double overallScore = (score != null) ? score.getOverallScore() : 80.0;
                    String badge = (score != null && score.getRecommendationBadge() != null) ? score.getRecommendationBadge() : "RECOMMENDED";

                    Map<String, Object> card = new HashMap<>();
                    card.put("productId", p.getId().toString());
                    card.put("id", p.getId().toString());
                    card.put("productName", p.getName());
                    card.put("name", p.getName());
                    card.put("brand", p.getBrand());
                    card.put("category", p.getCategory());
                    card.put("imageUrl", p.getImageUrl());
                    card.put("currentPrice", currentDisplay != null ? currentDisplay.doubleValue() : null);
                    card.put("price", currentDisplay != null ? currentDisplay.doubleValue() : null);
                    card.put("originalPrice", origDisplay != null ? origDisplay.doubleValue() : null);
                    card.put("discountPercentage", discount != null ? discount.doubleValue() : 0.0);
                    card.put("discount", discount != null ? discount.doubleValue() : 0.0);
                    card.put("currency", displayCurrency.name());
                    card.put("currencySymbol", displayCurrency.getSymbol());
                    card.put("recommendationScore", overallScore);
                    card.put("dealQuality", (p.getPrices() != null && !p.getPrices().isEmpty()) ? "AVAILABLE" : "UNKNOWN");
                    bundle.getGroundedProducts().add(card);

                    bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                            .productId(p.getId())
                            .productName(p.getName())
                            .factType("RECOMMENDATION_SCORE")
                            .description(String.format("Personalized Score: %.1f/100 (Badge: %s)", overallScore, badge))
                            .factualValue(overallScore)
                            .verified(true)
                            .confidence(0.90)
                            .build());
                }

                bundle.getSuggestedActions().add(AssistantAction.builder()
                        .type("EXPLORE_RECOMMENDATIONS")
                        .label("View All Personalized Picks")
                        .description("See personalized products scored against your shopping preferences")
                        .actionUrl("/recommendations")
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
            UserEntity user,
            CurrencyCode displayCurrency) {

        try {
            List<WatchlistResponseDTO> watchlists = priceWatchlistService.getAllWatchlists(user.getEmail());
            if (watchlists != null && !watchlists.isEmpty()) {
                for (WatchlistResponseDTO w : watchlists.stream().limit(5).collect(Collectors.toList())) {
                    BigDecimal targetDisplay = currencyConversionService.convertFromCanonical(w.getTargetPrice(), displayCurrency);
                    BigDecimal currentDisplay = currencyConversionService.convertFromCanonical(w.getCurrentBestPrice(), displayCurrency);
                    String targetStr = targetDisplay != null ? displayCurrency.getSymbol() + formatMoney(targetDisplay, displayCurrency) : "N/A";
                    String currentStr = currentDisplay != null ? displayCurrency.getSymbol() + formatMoney(currentDisplay, displayCurrency) : "N/A";

                    bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                            .productId(w.getProductId())
                            .productName(w.getProductName())
                            .factType("WATCHLIST")
                            .description(String.format("Target: %s | Current Best: %s", targetStr, currentStr))
                            .factualValue(targetDisplay)
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
                    .actionUrl("/watchlist")
                    .payload(Map.of())
                    .build());
        } catch (Exception e) {
            log.warn("Watchlist retrieval failed during assistant orchestration: {}", e.getMessage());
            bundle.getUnknownOrInsufficientData().add("Could not retrieve active watchlists");
        }
    }

    private void populatePreferenceEvidence(
            AssistantEvidenceBundle bundle,
            UserShoppingPreferenceDTO preferences,
            CurrencyCode displayCurrency) {

        if (preferences == null) {
            bundle.getUnknownOrInsufficientData().add("No custom shopping preferences configured");
        } else {
            CurrencyCode prefCur = preferences.getCurrency() != null ? preferences.getCurrency() : displayCurrency;
            String budgetDesc;
            if (preferences.getMinBudget() != null && preferences.getMaxBudget() != null) {
                budgetDesc = prefCur.getSymbol() + formatMoney(preferences.getMinBudget(), prefCur) + " - "
                        + prefCur.getSymbol() + formatMoney(preferences.getMaxBudget(), prefCur);
            } else if (preferences.getMaxBudget() != null) {
                budgetDesc = "Up to " + prefCur.getSymbol() + formatMoney(preferences.getMaxBudget(), prefCur);
            } else {
                budgetDesc = "Uncapped";
            }

            bundle.getFactualEvidence().add(GroundedEvidenceItem.builder()
                    .factType("USER_PREFERENCE")
                    .description(String.format("Budget: %s | Deal Sensitivity: %s | Availability: %s",
                            budgetDesc,
                            preferences.getDealSensitivity(),
                            preferences.getAvailabilityPreference()))
                    .verified(true)
                    .confidence(1.0)
                    .build());
        }

        bundle.getSuggestedActions().add(AssistantAction.builder()
                .type("UPDATE_PREFERENCES")
                .label("Adjust Shopping Preferences")
                .description("Update budget, categories, or brand preferences")
                .actionUrl("/preferences")
                .payload(Map.of())
                .build());
    }

    private void populateGeneralEvidence(AssistantEvidenceBundle bundle) {
        bundle.getSuggestedActions().add(AssistantAction.builder()
                .type("EXPLORE_TRENDING")
                .label("Show me trending products")
                .description("Check trending catalog items")
                .actionUrl("/trending")
                .payload(Map.of())
                .build());
        bundle.getSuggestedActions().add(AssistantAction.builder()
                .type("UPDATE_PREFERENCES")
                .label("Adjust Shopping Preferences")
                .description("Update shopping preferences")
                .actionUrl("/preferences")
                .payload(Map.of())
                .build());
    }

    private String generateAssistantText(
            String userQuery,
            UUID conversationId,
            AssistantIntent intent,
            AssistantEvidenceBundle bundle,
            UserShoppingPreferenceDTO preferences) {

        // Pure deterministic grounded generation strictly from authoritative PricePilot evidence bundle
        return fallbackGenerator.generateFallbackResponse(intent, bundle, preferences);
    }

    private AssistantResponseDTO buildResponseDTO(
            UUID conversationId,
            UUID messageId,
            AssistantIntent intent,
            String responseText,
            AssistantEvidenceBundle bundle,
            CurrencyCode displayCurrency) {

        List<String> suggestedPrompts = new ArrayList<>();
        String examplePrice = switch (displayCurrency != null ? displayCurrency : CurrencyCode.INR) {
            case INR -> "₹50000";
            case EUR -> "€1000";
            case GBP -> "£900";
            case JPY -> "¥150000";
            case USD -> "$1200";
        };
        if (intent == AssistantIntent.GENERAL) {
            suggestedPrompts.add("Find gaming laptops under " + examplePrice);
            suggestedPrompts.add("Compare iPhone 15 and Galaxy S24");
            suggestedPrompts.add("Show me trending products");
        } else if (intent == AssistantIntent.DISCOVERY) {
            suggestedPrompts.add("Compare top results");
            suggestedPrompts.add("Is now a good time to buy?");
        } else if (intent == AssistantIntent.PRICE_ANALYSIS) {
            suggestedPrompts.add("Set a price alert");
            suggestedPrompts.add("Find similar alternatives");
        }

        return AssistantResponseDTO.builder()
                .response(responseText)
                .conversationId(conversationId)
                .messageId(messageId)
                .intent(intent)
                .matchClassification(bundle != null ? bundle.getMatchClassification() : null)
                .requestedEntity(bundle != null ? bundle.getRequestedEntity() : null)
                .evidenceBundle(bundle)
                .products(bundle != null ? bundle.getGroundedProducts() : Collections.emptyList())
                .suggestedPrompts(suggestedPrompts)
                .suggestedActions(bundle != null ? bundle.getSuggestedActions() : Collections.emptyList())
                .build();
    }

    private String formatMoney(BigDecimal amount, CurrencyCode currency) {
        if (amount == null) return "N/A";
        int precision = currency != null ? currency.getDecimalPrecision() : 2;
        if (precision == 0) {
            return String.format(Locale.ROOT, "%.0f", amount);
        }
        return String.format(Locale.ROOT, "%." + precision + "f", amount);
    }

    private List<ProductEntity> findExplicitCatalogProductsForQuery(String query) {
        List<ProductEntity> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<ProductEntity> allProducts = productRepository.findAll();
        allProducts.sort((a, b) -> Integer.compare(b.getName().length(), a.getName().length()));

        for (ProductEntity p : allProducts) {
            String lowerName = p.getName().toLowerCase(Locale.ROOT);
            if (lowerQuery.contains(lowerName)) {
                if (results.stream().noneMatch(r -> r.getId().equals(p.getId()))) {
                    results.add(p);
                }
                continue;
            }
            if (p.getBrand() != null && lowerName.startsWith(p.getBrand().toLowerCase(Locale.ROOT) + " ")) {
                String withoutBrand = lowerName.substring(p.getBrand().length() + 1).trim();
                if (!withoutBrand.isEmpty() && lowerQuery.contains(withoutBrand)) {
                    if (results.stream().noneMatch(r -> r.getId().equals(p.getId()))) {
                        results.add(p);
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
