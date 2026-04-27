package ru.ticketswap.ticket;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.ticketswap.hold.ListingHold;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<TicketLot, Long>, JpaSpecificationExecutor<TicketLot> {

    @Override
    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAll();

    @Override
    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    Optional<TicketLot> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct t from TicketLot t left join fetch t.seller left join fetch t.buyer left join fetch t.ticketFiles left join fetch t.organizer left join fetch t.event where t.id = :id")
    Optional<TicketLot> findByIdForUpdate(@Param("id") Long id);
    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId);

    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAllByBuyerIdOrderByEventDateAsc(Long buyerId);

    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAllByOrganizerIdAndStatusOrderByCreatedAtAsc(Long organizerId, TicketStatus status);

    @EntityGraph(attributePaths = {"seller", "buyer", "ticketFiles", "organizer", "event"})
    List<TicketLot> findAllByOrganizerIdAndStatusAndBuyerIsNotNullOrderByCreatedAtAsc(Long organizerId, TicketStatus status);

    default Page<TicketLot> searchPublicListings(
            TicketStatus status,
            LocalDateTime now,
            Instant holdNow,
            String query,
            boolean queryBlank,
            Long organizerId,
            Long eventDbId,
            String eventId,
            boolean eventIdBlank,
            String city,
            boolean cityBlank,
            String venue,
            boolean venueBlank,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            BigDecimal priceMin,
            BigDecimal priceMax,
            Pageable pageable
    ) {
        Specification<TicketLot> specification = (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("status"), status));
            predicates.add(criteriaBuilder.greaterThan(root.<LocalDateTime>get("eventDate"), now));
            predicates.add(criteriaBuilder.not(criteriaBuilder.exists(activeHoldSubquery(
                    root,
                    criteriaQuery.subquery(Integer.class),
                    criteriaBuilder,
                    holdNow
            ))));

            Join<TicketLot, ?> organizerJoin = root.join("organizer", JoinType.INNER);
            predicates.add(criteriaBuilder.isFalse(organizerJoin.<Boolean>get("banned")));

            Join<TicketLot, ?> eventJoin = root.join("event", JoinType.LEFT);

            if (!queryBlank && query != null && !query.isBlank()) {
                String pattern = containsPattern(query);
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("eventName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("venueName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("venueCity")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.<String>get("organizerName"), "")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.<String>get("additionalInfo"), "")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.<String>get("sellerComment"), "")), pattern)
                ));
            }

            if (organizerId != null) {
                predicates.add(criteriaBuilder.equal(organizerJoin.<Long>get("id"), organizerId));
            }

            if (eventDbId != null) {
                predicates.add(criteriaBuilder.equal(eventJoin.<Long>get("id"), eventDbId));
            }

            if (!eventIdBlank && eventId != null && !eventId.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(eventJoin.<String>get("eventId"), "")),
                        eventId
                ));
            }

            if (!cityBlank && city != null && !city.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("venueCity")), containsPattern(city)));
            }

            if (!venueBlank && venue != null && !venue.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("venueName")), containsPattern(venue)));
            }

            if (dateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<LocalDateTime>get("eventDate"), dateFrom));
            }

            if (dateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<LocalDateTime>get("eventDate"), dateTo));
            }

            if (priceMin != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<BigDecimal>get("resalePrice"), priceMin));
            }

            if (priceMax != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<BigDecimal>get("resalePrice"), priceMax));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        return findAll(specification, pageable);
    }

    private static Subquery<Integer> activeHoldSubquery(
            Root<TicketLot> root,
            Subquery<Integer> subquery,
            CriteriaBuilder criteriaBuilder,
            Instant holdNow
    ) {
        Root<ListingHold> holdRoot = subquery.from(ListingHold.class);
        subquery.select(criteriaBuilder.literal(1));
        subquery.where(
                criteriaBuilder.equal(holdRoot.get("listing"), root),
                criteriaBuilder.greaterThan(holdRoot.<Instant>get("holdUntil"), holdNow)
        );
        return subquery;
    }

    private static String containsPattern(String value) {
        return "%" + value.toLowerCase() + "%";
    }

    boolean existsByEvent_Id(Long eventId);
}
