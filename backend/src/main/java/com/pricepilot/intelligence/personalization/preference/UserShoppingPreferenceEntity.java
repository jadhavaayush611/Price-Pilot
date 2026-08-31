package com.pricepilot.intelligence.personalization.preference;

import com.pricepilot.common.BaseEntity;
import com.pricepilot.user.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_shopping_preferences", indexes = {
    @Index(name = "idx_user_shopping_prefs_user", columnList = "user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserShoppingPreferenceEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserEntity user;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_preferred_categories", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "category", nullable = false)
    @Builder.Default
    private Set<String> preferredCategories = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_preferred_brands", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "brand", nullable = false)
    @Builder.Default
    private Set<String> preferredBrands = new HashSet<>();

    @Column(name = "min_budget", precision = 10, scale = 2)
    private BigDecimal minBudget;

    @Column(name = "max_budget", precision = 10, scale = 2)
    private BigDecimal maxBudget;

    @Column(name = "min_rating")
    private Double minRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "deal_sensitivity", nullable = false, length = 30)
    @Builder.Default
    private DealSensitivity dealSensitivity = DealSensitivity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_sensitivity", nullable = false, length = 30)
    @Builder.Default
    private PriceSensitivity priceSensitivity = PriceSensitivity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_preference", nullable = false, length = 30)
    @Builder.Default
    private AvailabilityPreference availabilityPreference = AvailabilityPreference.ALL;
}
