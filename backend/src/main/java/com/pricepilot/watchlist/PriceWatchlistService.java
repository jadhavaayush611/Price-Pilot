package com.pricepilot.watchlist;

import com.pricepilot.exception.DuplicateWatchlistException;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.dto.CreateWatchlistRequestDTO;
import com.pricepilot.watchlist.dto.UpdateWatchlistRequestDTO;
import com.pricepilot.watchlist.dto.WatchlistResponseDTO;
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

    public PriceWatchlistService(
            PriceWatchlistRepository watchlistRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            ProductPriceRepository productPriceRepository) {
        this.watchlistRepository = watchlistRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productPriceRepository = productPriceRepository;
    }

    @Transactional
    public WatchlistResponseDTO createWatchlist(String email, CreateWatchlistRequestDTO requestDTO) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        ProductEntity product = productRepository.findById(requestDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + requestDTO.getProductId()));

        if (product.isArchived()) {
            throw new IllegalArgumentException("Product must be active");
        }

        if (watchlistRepository.existsByUserIdAndProductId(user.getId(), product.getId())) {
            throw new DuplicateWatchlistException("You are already watching this product");
        }

        BigDecimal targetPrice = requestDTO.getTargetPrice();
        if (targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Target price must be greater than zero");
        }

        BigDecimal bestPrice = productPriceRepository.findBestPriceByProductId(product.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cannot track product: no prices found for this product"));

        if (targetPrice.compareTo(bestPrice) >= 0) {
            throw new IllegalArgumentException("Target price must be less than the current best price (" + bestPrice + ")");
        }

        PriceWatchlistEntity watchlist = PriceWatchlistEntity.builder()
                .user(user)
                .product(product)
                .targetPrice(targetPrice)
                .currentBestPrice(bestPrice)
                .active(true)
                .build();

        PriceWatchlistEntity saved = watchlistRepository.save(watchlist);
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
            throw new IllegalArgumentException("Target price must be greater than zero");
        }

        BigDecimal bestPrice = productPriceRepository.findBestPriceByProductId(watchlist.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Cannot track product: no prices found for this product"));

        if (targetPrice.compareTo(bestPrice) >= 0) {
            throw new IllegalArgumentException("Target price must be less than the current best price (" + bestPrice + ")");
        }

        watchlist.setTargetPrice(targetPrice);
        if (requestDTO.getActive() != null) {
            watchlist.setActive(requestDTO.getActive());
        }

        // Keep currentBestPrice updated to current best price on modification
        watchlist.setCurrentBestPrice(bestPrice);

        PriceWatchlistEntity updated = watchlistRepository.save(watchlist);
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
