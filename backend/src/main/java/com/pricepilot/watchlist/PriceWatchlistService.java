package com.pricepilot.watchlist;

import com.pricepilot.exception.DuplicateWatchlistException;
import com.pricepilot.exception.ProductArchivedException;
import com.pricepilot.exception.InvalidWatchlistPriceException;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.dto.CreateWatchlistRequestDTO;
import com.pricepilot.watchlist.dto.UpdateWatchlistRequestDTO;
import com.pricepilot.watchlist.dto.WatchlistResponseDTO;
import com.pricepilot.analytics.ProductAnalyticsService;
import com.pricepilot.interaction.UserInteractionEventService;
import com.pricepilot.interaction.InteractionType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PriceWatchlistService {

    private final PriceWatchlistRepository watchlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ProductAnalyticsService productAnalyticsService;

    private final UserInteractionEventService eventService;
    private final com.pricepilot.recommendation.RecommendationCacheHelper cacheHelper;

    public PriceWatchlistService(
            PriceWatchlistRepository watchlistRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            ProductPriceRepository productPriceRepository,
            ProductAnalyticsService productAnalyticsService,
            UserInteractionEventService eventService,
            com.pricepilot.recommendation.RecommendationCacheHelper cacheHelper) {
        this.watchlistRepository = watchlistRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productPriceRepository = productPriceRepository;
        this.productAnalyticsService = productAnalyticsService;
        this.eventService = eventService;
        this.cacheHelper = cacheHelper;
    }

    @Transactional
    public WatchlistResponseDTO createWatchlist(String email, CreateWatchlistRequestDTO requestDTO) {
        UserEntity user;
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof com.pricepilot.security.UserPrincipal) {
            com.pricepilot.security.UserPrincipal principal = (com.pricepilot.security.UserPrincipal) authentication.getPrincipal();
            user = userRepository.getReferenceById(principal.getId());
        } else {
            user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        }

        Object[] productAndBestPrice = productRepository.findProductAndBestPrice(requestDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + requestDTO.getProductId()));

        ProductEntity product = (ProductEntity) productAndBestPrice[0];
        BigDecimal bestPrice = (BigDecimal) productAndBestPrice[1];

        if (product.isArchived()) {
            throw new ProductArchivedException("Product must be active");
        }

        if (watchlistRepository.existsByUserIdAndProductId(user.getId(), product.getId())) {
            throw new DuplicateWatchlistException("You are already watching this product");
        }

        BigDecimal targetPrice = requestDTO.getTargetPrice();
        if (targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidWatchlistPriceException("Target price must be greater than zero");
        }

        if (bestPrice == null) {
            throw new InvalidWatchlistPriceException("Cannot track product: no prices found for this product");
        }

        if (targetPrice.compareTo(bestPrice) >= 0) {
            throw new InvalidWatchlistPriceException("Target price must be less than the current best price (" + bestPrice + ")");
        }

        PriceWatchlistEntity watchlist = PriceWatchlistEntity.builder()
                .user(user)
                .product(product)
                .targetPrice(targetPrice)
                .currentBestPrice(bestPrice)
                .active(true)
                .build();

        PriceWatchlistEntity saved = watchlistRepository.save(watchlist);
        productAnalyticsService.incrementWatchlistCount(product.getId());
        cacheHelper.evictUserCaches(user.getId());

        eventService.trackEvent(
                email,
                product.getId(),
                null,
                InteractionType.WATCHLIST_CREATE,
                java.util.Map.of(
                        "productId", product.getId().toString(),
                        "productName", product.getName(),
                        "targetPrice", targetPrice.doubleValue(),
                        "currentBestPrice", bestPrice.doubleValue()
                )
        );

        return WatchlistResponseDTO.fromEntity(saved);
    }

    @Transactional
    public WatchlistResponseDTO updateWatchlist(String email, UUID watchlistId, UpdateWatchlistRequestDTO requestDTO) {
        PriceWatchlistEntity watchlist = watchlistRepository.findByIdWithRelations(watchlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist entry not found with id: " + watchlistId));

        if (!watchlist.getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("You do not have permission to access this watchlist");
        }

        BigDecimal targetPrice = requestDTO.getTargetPrice();
        if (targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidWatchlistPriceException("Target price must be greater than zero");
        }

        BigDecimal bestPrice = productPriceRepository.findBestPriceByProductId(watchlist.getProduct().getId())
                .orElseThrow(() -> new InvalidWatchlistPriceException("Cannot track product: no prices found for this product"));

        if (targetPrice.compareTo(bestPrice) >= 0) {
            throw new InvalidWatchlistPriceException("Target price must be less than the current best price (" + bestPrice + ")");
        }

        watchlist.setTargetPrice(targetPrice);
        if (requestDTO.getActive() != null) {
            watchlist.setActive(requestDTO.getActive());
        }

        watchlist.setCurrentBestPrice(bestPrice);

        PriceWatchlistEntity updated = watchlistRepository.save(watchlist);
        cacheHelper.evictUserCaches(watchlist.getUser().getId());
        return WatchlistResponseDTO.fromEntity(updated);
    }

    @Transactional
    public void deleteWatchlist(String email, UUID watchlistId) {
        PriceWatchlistEntity watchlist = watchlistRepository.findByIdWithRelations(watchlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist entry not found with id: " + watchlistId));

        if (!watchlist.getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("You do not have permission to access this watchlist");
        }

        watchlistRepository.delete(watchlist);
        productAnalyticsService.decrementWatchlistCount(watchlist.getProduct().getId());
        cacheHelper.evictUserCaches(watchlist.getUser().getId());

        eventService.trackEvent(
                email,
                watchlist.getProduct().getId(),
                null,
                InteractionType.WATCHLIST_DELETE,
                java.util.Map.of(
                        "productId", watchlist.getProduct().getId().toString(),
                        "productName", watchlist.getProduct().getName(),
                        "targetPrice", watchlist.getTargetPrice().doubleValue()
                )
        );
    }

    @Transactional(readOnly = true)
    public WatchlistResponseDTO getWatchlistById(String email, UUID watchlistId) {
        PriceWatchlistEntity watchlist = watchlistRepository.findByIdWithRelations(watchlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist entry not found with id: " + watchlistId));

        if (!watchlist.getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("You do not have permission to access this watchlist");
        }

        return WatchlistResponseDTO.fromEntity(watchlist);
    }

    @Transactional(readOnly = true)
    public List<WatchlistResponseDTO> getAllWatchlists(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        List<PriceWatchlistEntity> watchlists = watchlistRepository.findAllByUserIdWithProduct(user.getId());
        return watchlists.stream()
                .map(WatchlistResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // --- FUTURE-READY DESIGN METHODS ---

    /**
     * Finds all active watchlists where the current best price is less than or equal to the target price.
     * This is intended for future scheduled jobs to evaluate matching trigger states.
     */
    @Transactional(readOnly = true)
    public List<WatchlistResponseDTO> getTriggeredWatchlists() {
        return watchlistRepository.findAll().stream()
                .filter(pw -> pw.isActive() && pw.getCurrentBestPrice().compareTo(pw.getTargetPrice()) <= 0)
                .map(WatchlistResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Updates the current best price of all active watchlists using an optimized query.
     */
    @Transactional
    public void updateBestPricesForActiveWatchlists() {
        List<PriceWatchlistEntity> activeWatchlists = watchlistRepository.findAll().stream()
                .filter(PriceWatchlistEntity::isActive)
                .collect(Collectors.toList());

        List<UUID> productIds = activeWatchlists.stream()
                .map(pw -> pw.getProduct().getId())
                .distinct()
                .collect(Collectors.toList());

        if (productIds.isEmpty()) {
            return;
        }

        List<com.pricepilot.productprice.dto.BestPriceProjection> bestPrices = 
                productPriceRepository.findBestPricesByProductIds(productIds);

        java.util.Map<UUID, BigDecimal> bestPriceMap = bestPrices.stream()
                .collect(Collectors.toMap(
                        com.pricepilot.productprice.dto.BestPriceProjection::getProductId,
                        com.pricepilot.productprice.dto.BestPriceProjection::getBestPrice
                ));

        for (PriceWatchlistEntity watchlist : activeWatchlists) {
            BigDecimal latestBest = bestPriceMap.get(watchlist.getProduct().getId());
            if (latestBest != null) {
                watchlist.setCurrentBestPrice(latestBest);
            }
        }
        watchlistRepository.saveAll(activeWatchlists);
    }
}
