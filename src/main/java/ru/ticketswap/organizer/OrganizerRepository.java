package ru.ticketswap.organizer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizerRepository extends JpaRepository<Organizer, Long> {

    Optional<Organizer> findByContactEmailIgnoreCase(String contactEmail);

    Optional<Organizer> findByOrganizerCodeIgnoreCase(String organizerCode);

    Optional<Organizer> findByNameIgnoreCase(String name);

    List<Organizer> findAllByBannedFalseOrderByNameAscIdAsc();

    boolean existsByOrganizerCodeIgnoreCase(String organizerCode);

    boolean existsByContactEmailIgnoreCase(String contactEmail);

    boolean existsByNameIgnoreCase(String name);
}
