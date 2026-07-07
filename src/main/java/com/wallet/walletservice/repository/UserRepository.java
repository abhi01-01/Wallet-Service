package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.UserStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    @NonNull Optional<User> findById(@NonNull UUID id);
    boolean existsByEmail(String email);

    @Query("""
            SELECT u
            FROM User u
            WHERE u.accountStatus = :status
              AND (:ownerType IS NULL OR u.ownerType = :ownerType)
              AND (
                    :query IS NULL
                    OR :query = ''
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            ORDER BY u.email ASC
            """)
    List<User> findUserOptions(
            @Param("status") UserStatus status,
            @Param("ownerType") OwnerType ownerType,
            @Param("query") String query,
            Pageable pageable
    );
}
