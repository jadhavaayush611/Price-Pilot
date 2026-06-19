package com.pricepilot.seller;

import com.pricepilot.seller.dto.SellerRequestDTO;
import com.pricepilot.seller.dto.SellerResponseDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sellers")
@CrossOrigin(origins = "*")
public class SellerController {

    private final SellerService sellerService;

    public SellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    @PostMapping
    public ResponseEntity<SellerResponseDTO> createSeller(@Valid @RequestBody SellerRequestDTO requestDTO) {
        SellerResponseDTO createdSeller = sellerService.createSeller(requestDTO);
        return new ResponseEntity<>(createdSeller, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<SellerResponseDTO>> getAllSellers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SellerResponseDTO> sellers = sellerService.getAllSellers(search, pageable);
        return ResponseEntity.ok(sellers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SellerResponseDTO> getSellerById(@PathVariable UUID id) {
        SellerResponseDTO seller = sellerService.getSellerById(id);
        return ResponseEntity.ok(seller);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SellerResponseDTO> updateSeller(
            @PathVariable UUID id,
            @Valid @RequestBody SellerRequestDTO requestDTO) {
        SellerResponseDTO updatedSeller = sellerService.updateSeller(id, requestDTO);
        return ResponseEntity.ok(updatedSeller);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSeller(@PathVariable UUID id) {
        sellerService.deleteSeller(id);
        return ResponseEntity.noContent().build();
    }
}
