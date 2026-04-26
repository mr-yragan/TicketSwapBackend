package ru.ticketswap.venue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    Optional<Venue> findByNameIgnoreCaseAndAddressIgnoreCaseAndTimezone(
            String name,
            String address,
            String timezone
    );
}
