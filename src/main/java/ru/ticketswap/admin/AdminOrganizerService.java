package ru.ticketswap.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.admin.dto.CreateOrganizerRequest;
import ru.ticketswap.common.ConflictException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerRepository;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

@Service
public class AdminOrganizerService {

    private static final String ORGANIZER_ROLE = "ORGANIZER";

    private final OrganizerRepository organizerRepository;
    private final UserRepository userRepository;
    private final UserIdentityService userIdentityService;

    public AdminOrganizerService(
            OrganizerRepository organizerRepository,
            UserRepository userRepository,
            UserIdentityService userIdentityService
    ) {
        this.organizerRepository = organizerRepository;
        this.userRepository = userRepository;
        this.userIdentityService = userIdentityService;
    }

    @Transactional
    public Organizer createOrganizer(CreateOrganizerRequest request) {
        String name = request.name().trim();
        String apiKey = request.apiKey().trim();
        String contactEmail = userIdentityService.normalizeEmail(request.contactEmail());

        User user = userIdentityService.findUserByEmail(contactEmail)
                .orElseThrow(() -> new NotFoundException("Пользователь с такой контактной почтой не найден"));

        if (organizerRepository.existsByApiKeyIgnoreCase(apiKey)) {
            throw new ConflictException("Организатор с таким API-ключом уже существует");
        }

        if (organizerRepository.existsByContactEmailIgnoreCase(contactEmail)) {
            throw new ConflictException("Организатор с такой контактной почтой уже существует");
        }

        Organizer organizer = organizerRepository.save(new Organizer(name, apiKey, contactEmail));
        user.setRole(ORGANIZER_ROLE);
        userRepository.save(user);

        return organizer;
    }
}