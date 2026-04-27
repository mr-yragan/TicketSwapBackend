package ru.ticketswap.purchase;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByListingIdAndBuyerIdAndIdempotencyKey(Long listingId, Long buyerId, String idempotencyKey);

    Optional<PurchaseOrder> findFirstByListingIdAndBuyerIdOrderByCreatedAtDesc(Long listingId, Long buyerId);

    List<PurchaseOrder> findAllByListingIdAndStatusIn(Long listingId, Collection<PurchaseOrderStatus> statuses);

    @EntityGraph(attributePaths = {"listing", "buyer", "seller"})
    List<PurchaseOrder> findAllByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    @EntityGraph(attributePaths = {"listing", "buyer", "seller"})
    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();
}
