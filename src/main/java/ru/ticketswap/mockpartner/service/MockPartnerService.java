package ru.ticketswap.mockpartner.service;

import org.springframework.stereotype.Service;
import ru.ticketswap.mockpartner.data.MockPartnerEventData;
import ru.ticketswap.mockpartner.data.MockPartnerDataProvider;
import ru.ticketswap.mockpartner.dto.MockPartnerEventResponse;
import ru.ticketswap.mockpartner.dto.MockTicketReissueRequest;
import ru.ticketswap.mockpartner.dto.MockTicketReissueResponse;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyRequest;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyResponse;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class MockPartnerService {

    private final MockPartnerDataProvider mockPartnerDataProvider;
    private final MockPartnerOrganizerResolver mockPartnerOrganizerResolver;
    private final MockPartnerEventMapper mockPartnerEventMapper;
    private final MockTicketVerificationService mockTicketVerificationService;
    private final MockTicketReissueService mockTicketReissueService;

    public MockPartnerService(
            MockPartnerDataProvider mockPartnerDataProvider,
            MockPartnerOrganizerResolver mockPartnerOrganizerResolver,
            MockPartnerEventMapper mockPartnerEventMapper,
            MockTicketVerificationService mockTicketVerificationService,
            MockTicketReissueService mockTicketReissueService
    ) {
        this.mockPartnerDataProvider = mockPartnerDataProvider;
        this.mockPartnerOrganizerResolver = mockPartnerOrganizerResolver;
        this.mockPartnerEventMapper = mockPartnerEventMapper;
        this.mockTicketVerificationService = mockTicketVerificationService;
        this.mockTicketReissueService = mockTicketReissueService;
    }

    public List<MockPartnerEventResponse> getUpcomingEvents(String organizerCode) {
        String resolvedOrganizerCode = mockPartnerOrganizerResolver.requireSupportedOrganizer(organizerCode);
        Instant now = Instant.now();

        return mockPartnerDataProvider.getEventsByOrganizerCode(resolvedOrganizerCode).stream()
                .filter(event -> resolvedOrganizerCode.equals(event.organizerCode()))
                .filter(event -> event.startsAt().isAfter(now))
                .sorted(Comparator
                        .comparing((MockPartnerEventData event) -> event.startsAt())
                        .thenComparing(event -> event.externalEventId()))
                .map(mockPartnerEventMapper::toResponse)
                .toList();
    }

    public MockTicketVerifyResponse verifyTicket(String organizerCode, MockTicketVerifyRequest request) {
        String resolvedOrganizerCode = mockPartnerOrganizerResolver.requireSupportedOrganizer(organizerCode);
        return mockTicketVerificationService.verify(resolvedOrganizerCode, request);
    }

    public MockTicketReissueResponse reissueTicket(String organizerCode, MockTicketReissueRequest request) {
        String resolvedOrganizerCode = mockPartnerOrganizerResolver.requireSupportedOrganizer(organizerCode);
        return mockTicketReissueService.reissue(resolvedOrganizerCode, request);
    }
}
