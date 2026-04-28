package ru.ticketswap.partnermock;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/partners")
public class PartnerMockController {

    @GetMapping("/{organizerCode}/events")
    public ResponseEntity<List<PartnerEventResponse>> getEvents(@PathVariable String organizerCode) {
        return ResponseEntity.ok(List.of(
                new PartnerEventResponse(organizerCode + "-DEMO-1", "Partner Demo Event 1", Instant.parse("2030-09-10T16:00:00Z"), organizerCode),
                new PartnerEventResponse(organizerCode + "-DEMO-2", "Partner Demo Event 2", Instant.parse("2030-11-22T18:30:00Z"), organizerCode)
        ));
    }

    @PostMapping("/{organizerCode}/tickets/verify")
    public ResponseEntity<TicketVerifyResponse> verify(@PathVariable String organizerCode, @RequestBody TicketVerifyRequest request) {
        require(request.ticketUid(), "ticketUid");
        require(request.eventId(), "eventId");
        String uid = request.ticketUid().trim();
        String eventId = request.eventId().trim();
        if (uid.toUpperCase().contains("INVALID")) {
            return ResponseEntity.ok(new TicketVerifyResponse(false, uid, organizerCode, eventId, "Mock-проверка не пройдена"));
        }
        return ResponseEntity.ok(new TicketVerifyResponse(true, uid, organizerCode, eventId, null));
    }

    @PostMapping("/{organizerCode}/tickets/reissue")
    public ResponseEntity<TicketReissueResponse> reissue(@PathVariable String organizerCode, @RequestBody TicketReissueRequest request) {
        require(request.originalTicketUid(), "originalTicketUid");
        require(request.buyerEmail(), "buyerEmail");
        require(request.eventId(), "eventId");
        require(request.operationId(), "operationId");
        String uid = request.originalTicketUid().trim();
        if (uid.toUpperCase().contains("FAIL")) {
            return ResponseEntity.ok(new TicketReissueResponse(false, uid, null, organizerCode, request.eventId(), request.operationId(), "Mock-перевыпуск не выполнен"));
        }
        return ResponseEntity.ok(new TicketReissueResponse(true, uid, "REISSUED-" + organizerCode + "-" + uid, organizerCode, request.eventId(), request.operationId(), null));
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    public record PartnerEventResponse(String eventId, String name, Instant startsAt, String organizerCode) {}
    public record TicketVerifyRequest(@NotBlank String ticketUid, @NotBlank String eventId) {}
    public record TicketVerifyResponse(boolean valid, String ticketUid, String organizerCode, String eventId, String reason) {}
    public record TicketReissueRequest(@NotBlank String originalTicketUid, @NotBlank String buyerEmail, @NotBlank String eventId, @NotBlank String operationId) {}
    public record TicketReissueResponse(boolean success, String originalTicketUid, String newTicketUid, String organizerCode, String eventId, String operationId, String reason) {}
}
