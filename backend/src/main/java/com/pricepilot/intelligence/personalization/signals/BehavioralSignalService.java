package com.pricepilot.intelligence.personalization.signals;

import java.util.UUID;

public interface BehavioralSignalService {
    UserShoppingSignals extractSignals(UUID userId);
}
