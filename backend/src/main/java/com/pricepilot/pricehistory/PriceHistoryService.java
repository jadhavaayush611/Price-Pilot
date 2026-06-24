package com.pricepilot.pricehistory;

import com.pricepilot.pricehistory.dto.PriceHistoryResponseDTO;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.seller.SellerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PriceHistoryService {

    private final PriceHistoryRepository priceHistoryRepository;

    public PriceHistoryService(PriceHistoryRepository priceHistoryRepository) {
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<PriceHistoryResponseDTO> getAllPriceHistory(Pageable pageable) {
        return priceHistoryRepository.findAllWithRelations(pageable)
                .map(PriceHistoryResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<PriceHistoryResponseDTO> getPriceHistoryByProduct(UUID productId, Pageable pageable) {
        return priceHistoryRepository.findByProductIdWithRelations(productId, pageable)
                .map(PriceHistoryResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<PriceHistoryResponseDTO> getPriceHistoryBySeller(UUID sellerId, Pageable pageable) {
        return priceHistoryRepository.findBySellerIdWithRelations(sellerId, pageable)
                .map(PriceHistoryResponseDTO::fromEntity);
    }

    @Transactional
    public void recordPriceHistory(ProductEntity product, SellerEntity seller, BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice.compareTo(newPrice) == 0) {
            return; // Do NOT create history entries if oldPrice == newPrice
        }

        BigDecimal priceDifference = newPrice.subtract(oldPrice);
        BigDecimal changePercentage = BigDecimal.ZERO;

        if (oldPrice.compareTo(BigDecimal.ZERO) != 0) {
            changePercentage = priceDifference
                    .divide(oldPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        PriceHistoryEntity priceHistory = PriceHistoryEntity.builder()
                .product(product)
                .seller(seller)
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .priceDifference(priceDifference)
                .changePercentage(changePercentage)
                .changedAt(LocalDateTime.now())
                .build();

        priceHistoryRepository.save(priceHistory);
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryResponseDTO> getLargestPriceDrops(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return priceHistoryRepository.findLargestPriceDrops(pageable).stream()
                .map(PriceHistoryResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryResponseDTO> getLargestPriceIncreases(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return priceHistoryRepository.findLargestPriceIncreases(pageable).stream()
                .map(PriceHistoryResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryResponseDTO> getRecentPriceChanges(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return priceHistoryRepository.findRecentPriceChanges(pageable).stream()
                .map(PriceHistoryResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
