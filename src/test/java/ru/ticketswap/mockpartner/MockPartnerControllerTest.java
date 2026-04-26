package ru.ticketswap.mockpartner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.test.web.servlet.MockMvc;
import ru.ticketswap.event.Event;
import ru.ticketswap.event.EventRepository;
import ru.ticketswap.auth.JwtService;
import ru.ticketswap.config.JwtAuthenticationFilter;
import ru.ticketswap.config.RestAccessDeniedHandler;
import ru.ticketswap.config.RestAuthenticationEntryPoint;
import ru.ticketswap.config.SecurityConfig;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.mockpartner.config.MockPartnerOrganizerInterceptor;
import ru.ticketswap.mockpartner.config.MockPartnerWebConfig;
import ru.ticketswap.mockpartner.controller.MockPartnerController;
import ru.ticketswap.mockpartner.data.MockPartnerDataProvider;
import ru.ticketswap.mockpartner.exception.MockPartnerExceptionHandler;
import ru.ticketswap.mockpartner.service.MockPartnerEventMapper;
import ru.ticketswap.mockpartner.service.MockPartnerOrganizerResolver;
import ru.ticketswap.mockpartner.service.MockPartnerService;
import ru.ticketswap.mockpartner.service.MockTicketReissueService;
import ru.ticketswap.mockpartner.service.MockTicketVerificationService;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerRepository;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.venue.Venue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MockPartnerController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        MockPartnerWebConfig.class,
        MockPartnerOrganizerInterceptor.class,
        MockPartnerExceptionHandler.class,
        MockPartnerService.class,
        MockPartnerDataProvider.class,
        MockPartnerOrganizerResolver.class,
        MockPartnerEventMapper.class,
        MockTicketVerificationService.class,
        MockTicketReissueService.class,
        MockPartnerControllerTest.TestConfig.class
})
class MockPartnerControllerTest {

    @TestConfiguration
    static class TestConfig {

        @Bean
        TicketSwapProperties ticketSwapProperties() {
            TicketSwapProperties properties = new TicketSwapProperties();
            properties.getCors().setAllowedOrigins(List.of("http://localhost:3000"));
            return properties;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationProvider authenticationProvider;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserIdentityService userIdentityService;

    @MockBean
    private OrganizerRepository organizerRepository;

    @MockBean
    private EventRepository eventRepository;

    @BeforeEach
    void setUpOrganizerRepository() {
        when(organizerRepository.existsByApiKeyIgnoreCase("org1")).thenReturn(true);
        when(organizerRepository.existsByApiKeyIgnoreCase("org2")).thenReturn(true);
        when(organizerRepository.existsByApiKeyIgnoreCase("org3")).thenReturn(false);
        when(eventRepository.findAllByOrganizerApiKeyIgnoreCaseOrderByStartsAtAscIdAsc("org1"))
                .thenReturn(List.of(createEvent("1001", "org1")));
        when(eventRepository.findAllByOrganizerApiKeyIgnoreCaseOrderByStartsAtAscIdAsc("org2"))
                .thenReturn(List.of(createEvent("2001", "org2")));
    }

    @Test
    void getOrg1EventsReturns200AndOnlyOrg1Events() throws Exception {
        mockMvc.perform(get("/api/mock/partners/org1/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[*].organizerCode", everyItem(is("org1"))))
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void getOrg2EventsReturns200AndOnlyOrg2Events() throws Exception {
        mockMvc.perform(get("/api/mock/partners/org2/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[*].organizerCode", everyItem(is("org2"))))
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void getUnknownOrganizerEventsReturns404Json() throws Exception {
        mockMvc.perform(get("/api/mock/partners/org3/events"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Не найдено"))
                .andExpect(jsonPath("$.message").value("Организатор не найден: org3"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getCreatedOrganizerEventsReturns200Json() throws Exception {
        when(organizerRepository.existsByApiKeyIgnoreCase("org3")).thenReturn(true);
        when(eventRepository.findAllByOrganizerApiKeyIgnoreCaseOrderByStartsAtAscIdAsc("org3"))
                .thenReturn(List.of(createEvent("EVT-10001", "org3")));

        mockMvc.perform(get("/api/mock/partners/org3/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].externalEventId").value("EVT-10001"))
                .andExpect(jsonPath("$[0].organizerCode").value("org3"));
    }

    @Test
    void postVerifyWithValidBodyReturns200Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketUid": "TICKET-12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ticketUid").value("TICKET-12345"))
                .andExpect(jsonPath("$.organizerCode").value("org1"))
                .andExpect(jsonPath("$.valid").exists());
    }

    private Event createEvent(String eventId, String organizerCode) {
        Venue venue = new Venue("Арена " + organizerCode, "Адрес " + organizerCode, "Europe/Moscow");
        Organizer organizer = new Organizer("Организатор " + organizerCode, organizerCode, organizerCode + "@example.com");
        return new Event(
                eventId,
                "Концерт " + organizerCode,
                venue,
                organizer,
                Instant.parse("2031-01-01T10:00:00Z"),
                LocalDate.of(2031, 1, 1)
        );
    }

    @Test
    void postReissueWithValidBodyReturns200Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalTicketUid": "TICKET-12345",
                                  "buyerEmail": "buyer@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.originalTicketUid").value("TICKET-12345"))
                .andExpect(jsonPath("$.newTicketUid").value("REISSUED-org1-TICKET-12345"))
                .andExpect(jsonPath("$.organizerCode").value("org1"));
    }

    @Test
    void postReissueWithFailUidReturns200JsonFailure() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalTicketUid": "TICKET-FAIL-1",
                                  "buyerEmail": "buyer@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.newTicketUid").doesNotExist())
                .andExpect(jsonPath("$.reason").value("Mock-перевыпуск не выполнен"));
    }

    @Test
    void reissueWithoutOriginalTicketUidReturns400Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "buyerEmail": "buyer@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Некорректный запрос"))
                .andExpect(jsonPath("$.message").value("UID исходного билета не должен быть пустым"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void reissueWithoutBuyerEmailReturns400Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalTicketUid": "TICKET-12345"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Почта покупателя не должна быть пустой"));
    }

    @Test
    void repeatedVerifyRequestsWithSameTicketUidReturnSameResponse() throws Exception {
        String payload = """
                {
                  "ticketUid": "TICKET-12345"
                }
                """;

        String first = mockMvc.perform(post("/api/mock/partners/org1/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(post("/api/mock/partners/org1/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(first, second);
    }

    @Test
    void verifyWithoutTicketUidReturns400Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Некорректный запрос"))
                .andExpect(jsonPath("$.message").value("UID билета не должен быть пустым"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void verifyWithNullTicketUidReturns400Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketUid": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("UID билета не должен быть пустым"));
    }

    @Test
    void verifyWithEmptyTicketUidReturns400Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketUid": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("UID билета не должен быть пустым"));
    }

    @Test
    void verifyWithBlankTicketUidReturns400Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketUid": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("UID билета не должен быть пустым"));
    }

    @Test
    void verifyWithEmptyBodyReturns400Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Некорректный запрос"))
                .andExpect(jsonPath("$.message").value("Тело запроса не должно быть пустым"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void verifyWithMalformedJsonReturns400Json() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org1/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Некорректный запрос"))
                .andExpect(jsonPath("$.message").value("Некорректный JSON в запросе"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownOrganizerOnVerifyReturns404JsonEvenWhenBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org3/tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Не найдено"))
                .andExpect(jsonPath("$.message").value("Организатор не найден: org3"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unknownOrganizerOnReissueReturns404JsonEvenWhenBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/mock/partners/org3/tickets/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Не найдено"))
                .andExpect(jsonPath("$.message").value("Организатор не найден: org3"))
                .andExpect(jsonPath("$.status").value(404));
    }
}