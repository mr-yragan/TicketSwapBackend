package ru.ticketswap.ticket;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.ticket.dto.CreateTicketRequest;
import ru.ticketswap.ticket.dto.TicketLotResponse;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

import java.math.BigDecimal;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketController(TicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sellTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        BigDecimal maxPrice = request.originalPrice().multiply(new BigDecimal("1.20"));
        if (request.resalePrice().compareTo(maxPrice) > 0) {
            throw new BusinessRuleException("Resale price cannot exceed original price by more than 20%");
        }


        if (userDetails == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        User seller = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Unauthorized"));


        TicketLot ticket = new TicketLot(
                request.uid(),
                request.eventName(),
                request.eventDate(),
                request.originalPrice(),
                request.resalePrice(),
                seller
        );

        ticketRepository.save(ticket);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(ticket));
    }

    @GetMapping
    public ResponseEntity<List<TicketLotResponse>> listTickets() {
        List<TicketLotResponse> items = ticketRepository.findAll().stream()
                .map(this::toResponse)
                .collect(toList());
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketLotResponse> getTicket(@PathVariable Long id) {
        TicketLot ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        return ResponseEntity.ok(toResponse(ticket));
    }

    @GetMapping("/my")
    public ResponseEntity<List<TicketLotResponse>> myTickets(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        User seller = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Unauthorized"));

        List<TicketLotResponse> items = ticketRepository.findAllBySellerId(seller.getId()).stream()
                .map(this::toResponse)
                .collect(toList());
        return ResponseEntity.ok(items);
    }

    private TicketLotResponse toResponse(TicketLot ticket) {
        String sellerEmail = null;
        try {
            sellerEmail = ticket.getSeller() != null ? ticket.getSeller().getEmail() : null;
        } catch (Exception ignored) {
            // seller is LAZY; but within controller call it should be available in transaction
        }
        return new TicketLotResponse(
                ticket.getId(),
                ticket.getUid(),
                ticket.getEventName(),
                ticket.getEventDate(),
                ticket.getOriginalPrice(),
                ticket.getResalePrice(),
                ticket.getStatus(),
                sellerEmail,
                ticket.getCreatedAt()
        );
    }
}
