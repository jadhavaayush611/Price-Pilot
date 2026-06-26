package com.pricepilot.exception;

import java.math.BigDecimal;

public class InvalidWatchlistPriceException extends RuntimeException {
    private final String errorCode;
    private final BigDecimal currentBestPrice;
    private final String baselineCurrency;

    public InvalidWatchlistPriceException(String message) {
        super(message);
        this.errorCode = null;
        this.currentBestPrice = null;
        this.baselineCurrency = null;
    }

    public InvalidWatchlistPriceException(String errorCode, String message, BigDecimal currentBestPrice, String baselineCurrency) {
        super(message);
        this.errorCode = errorCode;
        this.currentBestPrice = currentBestPrice;
        this.baselineCurrency = baselineCurrency;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public BigDecimal getCurrentBestPrice() {
        return currentBestPrice;
    }

    public String getBaselineCurrency() {
        return baselineCurrency;
    }
}
