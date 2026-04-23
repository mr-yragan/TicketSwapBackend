package ru.ticketswap.mockpartner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ticketswap.mockpartner.data.MockPartnerDataProvider;
import ru.ticketswap.mockpartner.data.MockPartnerEventData;
import ru.ticketswap.mockpartner.dto.MockPartnerEventResponse;
import ru.ticketswap.mockpartner.service.MockPartnerEventMapper;
import ru.ticketswap.mockpartner.service.MockPartnerOrganizerResolver;
import ru.ticketswap.mockpartner.service.MockPartnerService;
import ru.ticketswap.mockpartner.service.MockTicketReissueService;
import ru.ticketswap.mockpartner.service.MockTicketVerificationService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockPartnerServiceTest {

    @Mock
    private MockPartnerDataProvider mockPartnerDataProvider;

    @Mock
    private MockPartnerOrganizerResolver mockPartnerOrganizerResolver;

    @Mock
    private MockTicketVerificationService mockTicketVerificationService;

    @Mock
    private MockTicketReissueService mockTicketReissueService;

    @Test
    void getUpcomingEventsFiltersByOrganizerFutureDateSortsAndComputesLocalDate() {
        MockPartnerService service = new MockPartnerService(
                mockPartnerDataProvider,
                mockPartnerOrganizerResolver,
                new MockPartnerEventMapper(),
                mockTicketVerificationService,
                mockTicketReissueService
        );

        Instant now = Instant.now();
        MockPartnerEventData wrongOrganizer = new MockPartnerEventData(
                3001,
                "Концерт группы Сплин",
                now.plusSeconds(10_000),
                "org2",
                "Ледовый дворец",
                "Санкт-Петербург, проспект Пятилеток",
                "Europe/Moscow"
        );
        MockPartnerEventData pastEvent = new MockPartnerEventData(
                1000,
                "Прошедший концерт Мумий Тролль",
                now.minusSeconds(3_600),
                "org1",
                "ВТБ Арена",
                "Москва, Ленинградский проспект",
                "Europe/Moscow"
        );
        MockPartnerEventData sameStartsAtHigherId = new MockPartnerEventData(
                1003,
                "Концерт ДДТ",
                Instant.parse("2031-08-29T22:30:00Z"),
                "org1",
                "Лужники",
                "Москва, улица Лужники",
                "Europe/Moscow"
        );
        MockPartnerEventData sameStartsAtLowerId = new MockPartnerEventData(
                1002,
                "Концерт Би-2",
                Instant.parse("2031-08-29T22:30:00Z"),
                "org1",
                "Лужники",
                "Москва, улица Лужники",
                "Europe/Moscow"
        );
        MockPartnerEventData laterEvent = new MockPartnerEventData(
                1004L,
                "Концерт Земфиры",
                Instant.parse("2031-08-30T01:00:00Z"),
                "org1",
                "ВТБ Арена",
                "Москва, Ленинградский проспект",
                "Europe/Moscow"
        );

        when(mockPartnerOrganizerResolver.requireSupportedOrganizer("org1")).thenReturn("org1");
        when(mockPartnerDataProvider.getEventsByOrganizerCode("org1")).thenReturn(List.of(
                laterEvent,
                wrongOrganizer,
                sameStartsAtHigherId,
                pastEvent,
                sameStartsAtLowerId
        ));

        List<MockPartnerEventResponse> response = service.getUpcomingEvents("org1");

        assertEquals(3, response.size());
        assertTrue(response.stream().allMatch(event -> "org1".equals(event.organizerCode())));
        assertEquals(List.of(1002L, 1003L, 1004L), response.stream().map(MockPartnerEventResponse::externalEventId).toList());
        assertEquals(LocalDate.of(2031, 8, 30), response.get(0).date());
    }

    @Test
    void getUpcomingEventsReturnsEmptyListWhenOrganizerHasNoUpcomingEvents() {
        MockPartnerService service = new MockPartnerService(
                mockPartnerDataProvider,
                mockPartnerOrganizerResolver,
                new MockPartnerEventMapper(),
                mockTicketVerificationService,
                mockTicketReissueService
        );

        when(mockPartnerOrganizerResolver.requireSupportedOrganizer("org1")).thenReturn("org1");
        when(mockPartnerDataProvider.getEventsByOrganizerCode("org1")).thenReturn(List.of());

        assertTrue(service.getUpcomingEvents("org1").isEmpty());
    }
}
