package ru.ticketswap.organizer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizerRepository extends JpaRepository<Organizer, Long> {

    Optional<Organizer> findByContactEmailIgnoreCase(String contactEmail);

    Optional<Organizer> findByApiKeyIgnoreCase(String apiKey);

    boolean existsByApiKeyIgnoreCase(String apiKey);

    boolean existsByContactEmailIgnoreCase(String contactEmail);
}
