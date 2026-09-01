package com.pricepilot.intelligence.assistant;

import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import com.pricepilot.intelligence.assistant.intent.ShoppingIntentClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShoppingIntentClassifierTest {

    private ShoppingIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ShoppingIntentClassifier();
    }

    @Test
    @DisplayName("Classify comparison intents")
    void testComparisonIntents() {
        assertEquals(AssistantIntent.COMPARISON, classifier.classifyIntent("Compare iPhone 16 and Galaxy S24"));
        assertEquals(AssistantIntent.COMPARISON, classifier.classifyIntent("Dell XPS vs MacBook Pro"));
        assertEquals(AssistantIntent.COMPARISON, classifier.classifyIntent("What is the difference between these two laptops?"));
        assertEquals(AssistantIntent.COMPARISON, classifier.classifyIntent("Which is better between Sony and Bose?"));
    }

    @Test
    @DisplayName("Classify price analysis intents")
    void testPriceAnalysisIntents() {
        assertEquals(AssistantIntent.PRICE_ANALYSIS, classifier.classifyIntent("Show me price history for Sony headphones"));
        assertEquals(AssistantIntent.PRICE_ANALYSIS, classifier.classifyIntent("Is now a good time to buy?"));
        assertEquals(AssistantIntent.PRICE_ANALYSIS, classifier.classifyIntent("Should I buy now or wait for price drop?"));
        assertEquals(AssistantIntent.PRICE_ANALYSIS, classifier.classifyIntent("Has this reached its historical low?"));
    }

    @Test
    @DisplayName("Classify watchlist and alert action intents")
    void testWatchlistIntents() {
        assertEquals(AssistantIntent.WATCHLIST_ACTION, classifier.classifyIntent("Add this laptop to my watchlist"));
        assertEquals(AssistantIntent.WATCHLIST_ACTION, classifier.classifyIntent("Set alert when price drops below $800"));
        assertEquals(AssistantIntent.WATCHLIST_ACTION, classifier.classifyIntent("Notify me if target price is reached"));
        assertEquals(AssistantIntent.WATCHLIST_ACTION, classifier.classifyIntent("Show my price alerts"));
    }

    @Test
    @DisplayName("Classify user preference query intents")
    void testPreferenceQueryIntents() {
        assertEquals(AssistantIntent.PREFERENCE_QUERY, classifier.classifyIntent("What are my preferences?"));
        assertEquals(AssistantIntent.PREFERENCE_QUERY, classifier.classifyIntent("Show my budget settings"));
        assertEquals(AssistantIntent.PREFERENCE_QUERY, classifier.classifyIntent("What are my shopping preferences?"));
    }

    @Test
    @DisplayName("Classify personalized recommendation intents")
    void testRecommendationIntents() {
        assertEquals(AssistantIntent.RECOMMENDATION, classifier.classifyIntent("Recommend a phone for me"));
        assertEquals(AssistantIntent.RECOMMENDATION, classifier.classifyIntent("What should I buy for my budget?"));
        assertEquals(AssistantIntent.RECOMMENDATION, classifier.classifyIntent("Give me personalized top picks"));
    }

    @Test
    @DisplayName("Classify discovery and search intents")
    void testDiscoveryIntents() {
        assertEquals(AssistantIntent.DISCOVERY, classifier.classifyIntent("Find gaming laptops under $1200"));
        assertEquals(AssistantIntent.DISCOVERY, classifier.classifyIntent("Show me wireless headphones"));
        assertEquals(AssistantIntent.DISCOVERY, classifier.classifyIntent("Looking for cheapest 4k monitor"));
    }

    @Test
    @DisplayName("Classify general greetings and chatter")
    void testGeneralIntents() {
        assertEquals(AssistantIntent.GENERAL, classifier.classifyIntent("Hello there!"));
        assertEquals(AssistantIntent.GENERAL, classifier.classifyIntent("Who are you?"));
        assertEquals(AssistantIntent.GENERAL, classifier.classifyIntent(""));
        assertEquals(AssistantIntent.GENERAL, classifier.classifyIntent(null));
    }

    @Test
    @DisplayName("Extract price constraints from natural language query")
    void testExtractPriceConstraint() {
        assertEquals(1200.0, classifier.extractPriceConstraint("Find gaming laptops under $1200"));
        assertEquals(500.50, classifier.extractPriceConstraint("Show phones below 500.50"));
        assertEquals(800.0, classifier.extractPriceConstraint("Looking for items with budget of $800"));
        assertNull(classifier.extractPriceConstraint("Find laptops without price limit"));
    }
}
