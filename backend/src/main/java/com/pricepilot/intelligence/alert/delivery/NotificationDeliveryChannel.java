package com.pricepilot.intelligence.alert.delivery;

import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;

/**
 * Pluggable notification delivery channel interface.
 * Decouples alert generation logic from channel-specific delivery implementations.
 */
public interface NotificationDeliveryChannel {

    String channelName();

    boolean deliver(PriceAlertEntity alert);
}
