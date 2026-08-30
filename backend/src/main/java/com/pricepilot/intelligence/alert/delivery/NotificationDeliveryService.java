package com.pricepilot.intelligence.alert.delivery;

import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationDeliveryService {

    private final List<NotificationDeliveryChannel> channels;

    public NotificationDeliveryService(List<NotificationDeliveryChannel> channels) {
        this.channels = channels;
    }

    public void dispatch(PriceAlertEntity alert) {
        if (alert == null) {
            return;
        }

        for (NotificationDeliveryChannel channel : channels) {
            channel.deliver(alert);
        }
    }
}
