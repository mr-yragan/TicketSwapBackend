package ru.ticketswap.organizer;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import ru.ticketswap.common.ForbiddenException;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

@Service
public class OrganizerLookupService {

    private final UserRepository userRepository;
    private final OrganizerRepository organizerRepository;

    public OrganizerLookupService(UserRepository userRepository, OrganizerRepository organizerRepository) {
        this.userRepository = userRepository;
        this.organizerRepository = organizerRepository;
    }

    public OrganizerContext requireOrganizerContext(UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            throw new UnauthorizedException("Не авторизован");
        }

        User user = userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Не авторизован"));

        Organizer organizer = organizerRepository.findByContactEmailIgnoreCase(user.getEmail())
                .orElseThrow(() -> new ForbiddenException("Профиль организатора не привязан к организации"));

        return new OrganizerContext(user, organizer);
    }

    public record OrganizerContext(User user, Organizer organizer) {
    }
}