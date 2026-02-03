package ru.ticketswap.hold;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ListingHoldRepository extends JpaRepository<ListingHold, Long> {

    List<ListingHold> findAllByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<ListingHold> findAllByBuyerIdAndHoldUntilAfterOrderByHoldUntilAsc(Long buyerId, Instant now);

    Optional<ListingHold> findByListingId(Long listingId);

    @Query("""
        select h
        from ListingHold h
        join fetch h.listing l
        where h.buyer.id = :buyerId
          and h.holdUntil > :now
        order by h.holdUntil asc
    """)
    List<ListingHold> findActiveByBuyerIdWithListing(
            @Param("buyerId") Long buyerId,
            @Param("now") Instant now
    );
}
