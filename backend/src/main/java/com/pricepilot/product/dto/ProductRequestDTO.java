package com.pricepilot.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {

    @NotBlank(message = "Product name is required")
    private String name;

    private String brand;

    @NotBlank(message = "Product category is required")
    private String category;

    private String description;

    private String imageUrl;

    private boolean archived;
}
