package com.pricepilot.seller;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.seller.dto.SellerRequestDTO;
import com.pricepilot.seller.dto.SellerResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SellerService {

    private final SellerRepository sellerRepository;

    public SellerService(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    @Transactional
    public SellerResponseDTO createSeller(SellerRequestDTO requestDTO) {
        SellerEntity entity = SellerEntity.builder()
                .name(requestDTO.getName())
                .websiteUrl(requestDTO.getWebsiteUrl())
                .logoUrl(requestDTO.getLogoUrl())
                .build();

        SellerEntity savedEntity = sellerRepository.save(entity);
        return SellerResponseDTO.fromEntity(savedEntity);
    }

    @Transactional(readOnly = true)
    public Page<SellerResponseDTO> getAllSellers(String search, Pageable pageable) {
        Page<SellerEntity> entityPage;
        if (search != null && !search.trim().isEmpty()) {
            entityPage = sellerRepository.findByNameContainingIgnoreCase(search, pageable);
        } else {
            entityPage = sellerRepository.findAll(pageable);
        }
        return entityPage.map(SellerResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public SellerResponseDTO getSellerById(UUID id) {
        SellerEntity entity = sellerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + id));
        return SellerResponseDTO.fromEntity(entity);
    }

    @Transactional
    public SellerResponseDTO updateSeller(UUID id, SellerRequestDTO requestDTO) {
        SellerEntity entity = sellerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + id));

        entity.setName(requestDTO.getName());
        entity.setWebsiteUrl(requestDTO.getWebsiteUrl());
        entity.setLogoUrl(requestDTO.getLogoUrl());

        SellerEntity updatedEntity = sellerRepository.save(entity);
        return SellerResponseDTO.fromEntity(updatedEntity);
    }

    @Transactional
    public void deleteSeller(UUID id) {
        if (!sellerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Seller not found with id: " + id);
        }
        sellerRepository.deleteById(id);
    }
}
