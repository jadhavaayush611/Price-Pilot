package com.pricepilot.intelligence.personalization.preference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserShoppingPreferenceRepository extends JpaRepository<UserShoppingPreferenceEntity, UUID> {
    Optional<UserShoppingPreferenceEntity> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
