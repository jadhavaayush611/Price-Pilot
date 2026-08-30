package com.pricepilot.intelligence.alert.delivery;

import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationChannel implements NotificationDeliveryChannel {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationChannel.class);

    private final Counter successCounter;
    private final Counter failureCounter;

    public InAppNotificationChannel(MeterRegistry meterRegistry) {
        this.successCounter = Counter.builder("pricepilot.alerts.delivery.success")
                .tag("delivery_channel", "in_app")
                .description("Successfully delivered in-app price alerts")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("pricepilot.alerts.delivery.failure")
                .tag("delivery_channel", "in_app")
                .description("Failed in-app price alert deliveries")
                .register(meterRegistry);
    }

    @Override
    public String channelName() {
        return "in_app";
    }

    @Override
    public boolean deliver(PriceAlertEntity alert) {
        try {
            log.info("Delivering in-app price alert [id={}, type={}, user={}, product={}]",
                    alert.getId(), alert.getAlertType(),
                    alert.getUser() != null ? alert.getUser().getId() : null,
                    alert.getProduct() != null ? alert.getProduct().getId() : null);
            successCounter.increment();
            return true;
        } catch (Exception e) {
            log.error("Failed to deliver in-app price alert [id={}]", alert.getId(), e);
            failureCounter.increment();
            return false;
        }
    }
}
