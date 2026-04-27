package ru.ticketswap.organizer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.organizer.dto.OrganizerCatalogResponse;

import java.util.List;

@RestController
@RequestMapping("/api/organizers")
public class OrganizerCatalogController {

    private final OrganizerRepository organizerRepository;

    public OrganizerCatalogController(OrganizerRepository organizerRepository) {
        this.organizerRepository = organizerRepository;
    }

    @GetMapping
    public ResponseEntity<List<OrganizerCatalogResponse>> listOrganizers() {
        List<OrganizerCatalogResponse> response = organizerRepository.findAllByBannedFalseOrderByNameAscIdAsc().stream()
                .map(OrganizerCatalogResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }
}
