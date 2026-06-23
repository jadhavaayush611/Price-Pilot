package com.pricepilot.productprice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public interface BestPriceProjection {
    UUID getProductId();
    BigDecimal getBestPrice();
}
