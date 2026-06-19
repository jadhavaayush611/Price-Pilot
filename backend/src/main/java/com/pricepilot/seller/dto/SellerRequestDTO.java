package com.pricepilot.seller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerRequestDTO {

    @NotBlank(message = "Seller name is required")
    private String name;

    private String websiteUrl;

    private String logoUrl;
}
