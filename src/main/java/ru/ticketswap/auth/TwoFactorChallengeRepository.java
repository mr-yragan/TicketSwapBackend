package ru.ticketswap.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select challenge
            from TwoFactorChallenge challenge
            join fetch challenge.user
            where challenge.challengeId = :challengeId
            """)
    Optional<TwoFactorChallenge> findForUpdateByChallengeId(@Param("challengeId") String challengeId);

    @Modifying
    @Query("""
            update TwoFactorChallenge challenge
            set challenge.consumedAt = :consumedAt
            where challenge.user.id = :userId
              and challenge.consumedAt is null
            """)
    int invalidateActiveChallenges(@Param("userId") Long userId, @Param("consumedAt") Instant consumedAt);
}
