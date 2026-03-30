package ru.ticketswap.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from PasswordResetToken token
            join fetch token.user
            where token.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update PasswordResetToken token
            set token.consumedAt = :consumedAt
            where token.user.id = :userId
              and token.consumedAt is null
            """)
    int invalidateActiveTokens(@Param("userId") Long userId, @Param("consumedAt") Instant consumedAt);

    @Modifying
    @Query("""
            update PasswordResetToken token
            set token.consumedAt = :consumedAt
            where token.user.id = :userId
              and token.consumedAt is null
              and token.tokenHash <> :tokenHash
            """)
    int invalidateActiveTokensExcept(
            @Param("userId") Long userId,
            @Param("tokenHash") String tokenHash,
            @Param("consumedAt") Instant consumedAt
    );
}
