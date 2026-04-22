package ru.ticketswap.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailVerificationToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update EmailVerificationToken t
               set t.consumedAt = :now
             where t.user.id = :userId
               and t.consumedAt is null
               and t.expiresAt > :now
            """)
    void invalidateActiveTokens(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update EmailVerificationToken t
               set t.consumedAt = :now
             where t.user.id = :userId
               and t.consumedAt is null
               and t.expiresAt > :now
               and t.tokenHash <> :tokenHash
            """)
    void invalidateActiveTokensExcept(
            @Param("userId") Long userId,
            @Param("tokenHash") String tokenHash,
            @Param("now") Instant now
    );
}
