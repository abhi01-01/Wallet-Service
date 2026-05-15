package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.User;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    @NonNull Optional<User> findById(UUID id);
    boolean existsByEmail(String email);
}
