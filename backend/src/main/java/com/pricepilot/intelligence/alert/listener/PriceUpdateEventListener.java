package com.pricepilot.intelligence.alert.listener;

import com.pricepilot.intelligence.alert.event.ProductPriceChangedEvent;
import com.pricepilot.intelligence.alert.service.PriceAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for price change events after transaction commits and asynchronously triggers alert evaluation.
 */
@Component
public class PriceUpdateEventListener {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateEventListener.class);

    private final PriceAlertService priceAlertService;

    public PriceUpdateEventListener(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductPriceChanged(ProductPriceChangedEvent event) {
        log.info("Processing asynchronous price alert evaluation for product: {}", event.productId());
        try {
            priceAlertService.processPriceUpdateEvent(
                    event.productId(),
                    event.oldPrice(),
                    event.newPrice(),
                    event.isBackInStock()
            );
        } catch (Exception e) {
            log.error("Error during asynchronous price alert evaluation for product: {}", event.productId(), e);
        }
    }
}
