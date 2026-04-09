package ru.ticketswap.ticket.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingStatusHistoryRepository extends JpaRepository<ListingStatusHistory, Long> {

    List<ListingStatusHistory> findAllByListingIdOrderByChangedAtAscIdAsc(Long listingId);
}
