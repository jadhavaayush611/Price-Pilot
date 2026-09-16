package com.pricepilot.intelligence.personalization.context;

/**
 * Identifies the provenance source of a personalization signal.
 * Preserves the domain principle: EXPLICIT_PREFERENCE > BEHAVIORAL_SIGNAL in confidence and priority.
 */
public enum PersonalizationSource {
    /**
     * Explicitly declared by the user via shopping preferences (e.g. preferred categories, budget limits).
     */
    EXPLICIT_PREFERENCE,

    /**
     * Inferred deterministically from aggregated user behavioral interactions (e.g. view frequency, search activity).
     */
    BEHAVIORAL_SIGNAL
}
