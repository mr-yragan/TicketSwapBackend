package ru.ticketswap.ticket;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.ticketswap.ticket.dto.CreateTicketRequest;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

import java.math.BigDecimal;

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
            @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        BigDecimal maxPrice = request.originalPrice().multiply(new BigDecimal("1.20"));
        if (request.resalePrice().compareTo(maxPrice) > 0) {
            return ResponseEntity.badRequest()
                    .body("Цена перепродажи не может быть выше оригинала более чем на 20%");
        }


        User seller = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));


        TicketLot ticket = new TicketLot(
                request.uid(),
                request.eventName(),
                request.eventDate(),
                request.originalPrice(),
                request.resalePrice(),
                seller
        );

        ticketRepository.save(ticket);

        return ResponseEntity.ok("Заявка на продажу создана");
    }
}
