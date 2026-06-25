package com.pricepilot.interaction;

import com.pricepilot.interaction.dto.AnalyticsCountProjection;
import com.pricepilot.interaction.dto.UserInteractionEventResponseDTO;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserInteractionEventService {

    private final UserInteractionEventRepository eventRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;

    public UserInteractionEventService(
            UserInteractionEventRepository eventRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            SellerRepository sellerRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.sellerRepository = sellerRepository;
    }

    /**
     * Records a new user interaction event asynchronously or synchronously.
     */
    @Transactional
    public void trackEvent(
            String userEmail,
            UUID productId,
            UUID sellerId,
            InteractionType interactionType,
            Map<String, Object> metadata) {

        UserEntity user = null;
        if (userEmail != null && !userEmail.trim().isEmpty()) {
            user = userRepository.findByEmail(userEmail).orElse(null);
        }

        ProductEntity product = null;
        if (productId != null) {
            product = productRepository.findById(productId).orElse(null);
        }

        SellerEntity seller = null;
        if (sellerId != null) {
            seller = sellerRepository.findById(sellerId).orElse(null);
        }

        UserInteractionEventEntity event = UserInteractionEventEntity.builder()
                .user(user)
                .product(product)
                .seller(seller)
                .interactionType(interactionType)
                .metadata(metadata != null ? metadata : Map.of())
                .build();

        eventRepository.save(event);
    }

    /**
     * Queries events with dynamic filtering and returns projections (DTOs).
     */
    @Transactional(readOnly = true)
    public Page<UserInteractionEventResponseDTO> getEvents(
            UUID userId,
            UUID productId,
            UUID sellerId,
            InteractionType interactionType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String search,
            Pageable pageable) {

        Specification<UserInteractionEventEntity> spec = UserInteractionEventSpecifications.withFilters(
                userId, productId, sellerId, interactionType, startDate, endDate, search);

        Page<UserInteractionEventEntity> eventsPage = eventRepository.findAll(spec, pageable);
        return eventsPage.map(this::mapToDTO);
    }

    /**
     * Map Entity to Response DTO to enforce encapsulation and avoid sending raw JPA structures.
     */
    public UserInteractionEventResponseDTO mapToDTO(UserInteractionEventEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserInteractionEventResponseDTO.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .userEmail(entity.getUser() != null ? entity.getUser().getEmail() : null)
                .productId(entity.getProduct() != null ? entity.getProduct().getId() : null)
                .productName(entity.getProduct() != null ? entity.getProduct().getName() : null)
                .sellerId(entity.getSeller() != null ? entity.getSeller().getId() : null)
                .sellerName(entity.getSeller() != null ? entity.getSeller().getName() : null)
                .interactionType(entity.getInteractionType())
                .metadata(entity.getMetadata())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    // --- ANALYTICS SERVICE LAYER METHODS (PART 10) ---

    @Transactional(readOnly = true)
    public List<AnalyticsCountProjection> getMostViewedCategories(int limit) {
        return eventRepository.findMostViewedCategories(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<AnalyticsCountProjection> getMostViewedBrands(int limit) {
        return eventRepository.findMostViewedBrands(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<AnalyticsCountProjection> getMostActiveUsers(int limit) {
        return eventRepository.findMostActiveUsers(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<AnalyticsCountProjection> getMostClickedSellers(int limit) {
        return eventRepository.findMostClickedSellers(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<AnalyticsCountProjection> getMostSearchedKeywords(int limit) {
        return eventRepository.findMostSearchedKeywords(PageRequest.of(0, limit));
    }
}
