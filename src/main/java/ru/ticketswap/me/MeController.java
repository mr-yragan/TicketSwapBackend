package ru.ticketswap.me;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.ticketswap.auth.TwoFactorService;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.hold.ListingHold;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.me.dto.HoldResponse;
import ru.ticketswap.me.dto.MeProfileResponse;
import ru.ticketswap.me.dto.TwoFactorStatusResponse;
import ru.ticketswap.me.dto.UpdateMeRequest;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.TicketStatus;
import ru.ticketswap.ticket.dto.TicketLotResponse;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final ListingHoldRepository listingHoldRepository;
    private final UserIdentityService userIdentityService;
    private final TwoFactorService twoFactorService;

    public MeController(
            UserRepository userRepository,
            TicketRepository ticketRepository,
            ListingHoldRepository listingHoldRepository,
            UserIdentityService userIdentityService,
            TwoFactorService twoFactorService
    ) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.listingHoldRepository = listingHoldRepository;
        this.userIdentityService = userIdentityService;
        this.twoFactorService = twoFactorService;
    }

    @GetMapping
    public ResponseEntity<MeProfileResponse> getProfile(@AuthenticationPrincipal UserDetails principal) {
        User user = requireUser(principal);
        return ResponseEntity.ok(toMeResponse(user));
    }

    @PatchMapping
    public ResponseEntity<MeProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateMeRequest request
    ) {
        User user = requireUser(principal);

        if (request.login() != null && !request.login().isBlank()) {
            String normalizedLogin = userIdentityService.normalizeLogin(request.login());
            userIdentityService.assertLoginAvailable(normalizedLogin, user.getId());
            user.setLogin(normalizedLogin);
        }

        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            String normalizedPhone = userIdentityService.normalizePhone(request.phoneNumber());
            userIdentityService.assertPhoneAvailable(normalizedPhone, user.getId());
            user.setPhoneNumber(normalizedPhone);
        }

        userRepository.save(user);
        return ResponseEntity.ok(toMeResponse(user));
    }

    @GetMapping("/2fa")
    public ResponseEntity<TwoFactorStatusResponse> getTwoFactorStatus(
            @AuthenticationPrincipal UserDetails principal
    ) {
        User user = requireUser(principal);
        return ResponseEntity.ok(new TwoFactorStatusResponse(user.isTwoFactorEnabled()));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<TwoFactorStatusResponse> enableTwoFactor(
            @AuthenticationPrincipal UserDetails principal
    ) {
        User user = requireUser(principal);
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        twoFactorService.invalidateChallengesForUser(user.getId());
        return ResponseEntity.ok(new TwoFactorStatusResponse(true));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<TwoFactorStatusResponse> disableTwoFactor(
            @AuthenticationPrincipal UserDetails principal
    ) {
        User user = requireUser(principal);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);
        twoFactorService.invalidateChallengesForUser(user.getId());
        return ResponseEntity.ok(new TwoFactorStatusResponse(false));
    }

    @GetMapping("/listings")
    public ResponseEntity<List<TicketLotResponse>> myListings(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "active") String scope
    ) {
        User user = requireUser(principal);

        LocalDateTime now = LocalDateTime.now();
        List<TicketLot> lots = ticketRepository.findAllBySellerIdOrderByCreatedAtDesc(user.getId());

        List<TicketLotResponse> res = lots.stream()
                .filter(lot -> filterByScopeForListings(lot, scope, now))
                .map(TicketLotResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(res);
    }

    @GetMapping("/holds")
    public ResponseEntity<List<HoldResponse>> myHolds(@AuthenticationPrincipal UserDetails principal) {
        User user = requireUser(principal);

        Instant now = Instant.now();
        List<ListingHold> holds = listingHoldRepository.findActiveByBuyerIdWithListing(user.getId(), now);

        List<HoldResponse> res = holds.stream()
                .map(h -> new HoldResponse(
                        h.getId(),
                        TicketLotResponse.fromEntity(h.getListing()),
                        h.getHoldUntil(),
                        h.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(res);
    }

    @GetMapping("/purchases")
    public ResponseEntity<List<TicketLotResponse>> myPurchases(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "active") String scope
    ) {
        User user = requireUser(principal);

        LocalDateTime now = LocalDateTime.now();
        List<TicketLot> purchases = ticketRepository.findAllByBuyerIdOrderByEventDateAsc(user.getId());

        List<TicketLotResponse> res = purchases.stream()
                .filter(t -> t.getStatus() == TicketStatus.COMPLETED)
                .filter(t -> filterByScopeForPurchases(t, scope, now))
                .map(TicketLotResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(res);
    }

    private User requireUser(UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }

    private MeProfileResponse toMeResponse(User user) {
        return new MeProfileResponse(
                user.getId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getLogin(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private boolean filterByScopeForListings(TicketLot lot, String scope, LocalDateTime now) {
        boolean isActive = lot.getStatus() != TicketStatus.COMPLETED && lot.getEventDate().isAfter(now);
        boolean isArchived = lot.getStatus() == TicketStatus.COMPLETED || !lot.getEventDate().isAfter(now);

        if ("active".equalsIgnoreCase(scope)) {
            return isActive;
        }
        if ("archived".equalsIgnoreCase(scope) || "archive".equalsIgnoreCase(scope)) {
            return isArchived;
        }
        return true;
    }

    private boolean filterByScopeForPurchases(TicketLot t, String scope, LocalDateTime now) {
        boolean isActive = !t.getEventDate().isBefore(now);
        boolean isArchived = t.getEventDate().isBefore(now);

        if ("active".equalsIgnoreCase(scope)) {
            return isActive;
        }
        if ("archived".equalsIgnoreCase(scope) || "archive".equalsIgnoreCase(scope)) {
            return isArchived;
        }
        return true;
    }
}
