package com.pricepilot.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {
    java.util.Optional<UserEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    default java.util.Optional<UserEntity> findByEmail(String email) {
        return findByEmailIgnoreCase(email != null ? email.trim() : null);
    }
    default boolean existsByEmail(String email) {
        return existsByEmailIgnoreCase(email != null ? email.trim() : null);
    }
}

