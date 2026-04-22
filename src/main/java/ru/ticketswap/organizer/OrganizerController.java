package ru.ticketswap.organizer;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.organizer.dto.OrganizerDashboardResponse;
import ru.ticketswap.organizer.dto.OrganizerProfileResponse;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

@RestController
@RequestMapping("/api/organizer")
public class OrganizerController {

    private final UserRepository userRepository;

    public OrganizerController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<OrganizerProfileResponse> getOrganizerProfile(
            @AuthenticationPrincipal UserDetails principal
    ) {
        User organizer = requireOrganizer(principal);

        return ResponseEntity.ok(new OrganizerProfileResponse(
                organizer.getId(),
                organizer.getEmail(),
                organizer.getLogin(),
                organizer.getRole(),
                organizer.isEmailVerified(),
                organizer.getCreatedAt()
        ));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<OrganizerDashboardResponse> getOrganizerDashboard(
            @AuthenticationPrincipal UserDetails principal
    ) {
        User organizer = requireOrganizer(principal);

        return ResponseEntity.ok(new OrganizerDashboardResponse(
                organizer.getId(),
                organizer.getEmail(),
                organizer.getRole(),
                true,
                0,
                "Mock organizer"
        ));
    }

    private User requireOrganizer(UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }
}
