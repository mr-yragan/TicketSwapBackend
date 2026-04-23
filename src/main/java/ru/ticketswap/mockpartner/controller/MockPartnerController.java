package ru.ticketswap.mockpartner.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.mockpartner.dto.MockPartnerEventResponse;
import ru.ticketswap.mockpartner.dto.MockTicketReissueRequest;
import ru.ticketswap.mockpartner.dto.MockTicketReissueResponse;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyRequest;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyResponse;
import ru.ticketswap.mockpartner.service.MockPartnerService;

import java.util.List;

@RestController
@RequestMapping("/api/mock/partners")
public class MockPartnerController {

    private final MockPartnerService mockPartnerService;

    public MockPartnerController(MockPartnerService mockPartnerService) {
        this.mockPartnerService = mockPartnerService;
    }

    @GetMapping("/{organizerCode}/events")
    public ResponseEntity<List<MockPartnerEventResponse>> getUpcomingEvents(@PathVariable String organizerCode) {
        return ResponseEntity.ok(mockPartnerService.getUpcomingEvents(organizerCode));
    }

    @PostMapping("/{organizerCode}/tickets/verify")
    public ResponseEntity<MockTicketVerifyResponse> verifyTicket(
            @PathVariable String organizerCode,
            @RequestBody(required = false) MockTicketVerifyRequest request
    ) {
        return ResponseEntity.ok(mockPartnerService.verifyTicket(organizerCode, request));
    }

    @PostMapping("/{organizerCode}/tickets/reissue")
    public ResponseEntity<MockTicketReissueResponse> reissueTicket(
            @PathVariable String organizerCode,
            @RequestBody(required = false) MockTicketReissueRequest request
    ) {
        return ResponseEntity.ok(mockPartnerService.reissueTicket(organizerCode, request));
    }
}
