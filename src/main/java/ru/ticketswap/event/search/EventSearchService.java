package ru.ticketswap.event.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.event.Event;
import ru.ticketswap.event.EventRepository;
import ru.ticketswap.event.search.dto.EventSearchResponse;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.venue.Venue;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class EventSearchService {

    private static final Logger log = LoggerFactory.getLogger(EventSearchService.class);

    private final RestClient elasticsearchClient;
    private final EventRepository eventRepository;
    private final TicketSwapProperties.Search.Elasticsearch properties;
    private final Clock clock;

    public EventSearchService(
            @Qualifier("elasticsearchRestClient") RestClient elasticsearchClient,
            EventRepository eventRepository,
            TicketSwapProperties ticketSwapProperties,
            Clock clock
    ) {
        this.elasticsearchClient = elasticsearchClient;
        this.eventRepository = eventRepository;
        this.properties = ticketSwapProperties.getSearch().getElasticsearch();
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndexOnStartup() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            ensureIndexExists();
            reindexUpcomingEvents();
        } catch (RuntimeException ex) {
            log.warn("Elasticsearch index initialization failed; event search will use DB fallback until Elasticsearch is available", ex);
        }
    }

    public List<EventSearchResponse> searchUpcomingEvents(String query, Long organizerId, int limit) {
        if (properties.isEnabled()) {
            try {
                List<EventSearchResponse> elasticResults = searchInElasticsearch(query, organizerId, limit);
                if (!elasticResults.isEmpty() || query == null || query.isBlank()) {
                    return elasticResults;
                }
            } catch (RuntimeException ex) {
                log.warn("Elasticsearch event search failed; falling back to database search", ex);
            }
        }

        return searchInDatabase(query, organizerId, limit);
    }

    public void indexEvent(Event event) {
        if (!properties.isEnabled() || event == null || event.getId() == null) {
            return;
        }

        try {
            ensureIndexExists();
            elasticsearchClient
                    .put()
                    .uri("/{index}/_doc/{id}?refresh=wait_for", properties.getIndexName(), event.getId())
                    .body(toDocument(event))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Failed to index event {} in Elasticsearch", event.getId(), ex);
        }
    }

    public void deleteEvent(Long eventId) {
        if (!properties.isEnabled() || eventId == null) {
            return;
        }

        try {
            elasticsearchClient
                    .delete()
                    .uri("/{index}/_doc/{id}?refresh=wait_for", properties.getIndexName(), eventId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // Document already absent from index.
        } catch (RuntimeException ex) {
            log.warn("Failed to delete event {} from Elasticsearch", eventId, ex);
        }
    }

    private void ensureIndexExists() {
        try {
            elasticsearchClient
                    .put()
                    .uri("/{index}", properties.getIndexName())
                    .body(indexDefinition())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest ex) {
            if (ex.getResponseBodyAsString() == null || !ex.getResponseBodyAsString().contains("resource_already_exists_exception")) {
                throw ex;
            }
        }
    }

    private void reindexUpcomingEvents() {
        List<Event> events = eventRepository.findUpcomingEvents(clock.instant());
        for (Event event : events) {
            indexEvent(event);
        }
        log.info("Reindexed {} upcoming events into Elasticsearch index {}", events.size(), properties.getIndexName());
    }

    @SuppressWarnings("unchecked")
    private List<EventSearchResponse> searchInElasticsearch(String query, Long organizerId, int limit) {
        Map<String, Object> response = elasticsearchClient
                .post()
                .uri("/{index}/_search", properties.getIndexName())
                .body(searchBody(query, organizerId, limit))
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return List.of();
        }

        Object hitsObject = response.get("hits");
        if (!(hitsObject instanceof Map<?, ?> hitsMap)) {
            return List.of();
        }

        Object hitsArray = hitsMap.get("hits");
        if (!(hitsArray instanceof List<?> hits)) {
            return List.of();
        }

        List<EventSearchResponse> result = new ArrayList<>();
        for (Object hitObject : hits) {
            if (!(hitObject instanceof Map<?, ?> hit)) {
                continue;
            }
            Object sourceObject = hit.get("_source");
            if (!(sourceObject instanceof Map<?, ?> source)) {
                continue;
            }
            EventSearchResponse mapped = fromDocument((Map<String, Object>) source);
            if (mapped != null) {
                result.add(mapped);
            }
        }
        return result;
    }

    private List<EventSearchResponse> searchInDatabase(String query, Long organizerId, int limit) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Event> events = eventRepository.searchUpcomingEvents(normalizedQuery, organizerId, clock.instant());
        return events.stream()
                .limit(limit)
                .map(EventSearchResponse::fromEntity)
                .toList();
    }

    private Map<String, Object> indexDefinition() {
        Map<String, Object> keyword = Map.of("type", "keyword");
        Map<String, Object> text = Map.of("type", "text");
        Map<String, Object> searchAsYouType = Map.of("type", "search_as_you_type");

        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("id", Map.of("type", "long"));
        propertiesMap.put("eventId", searchAsYouType);
        propertiesMap.put("name", searchAsYouType);
        propertiesMap.put("startsAt", Map.of("type", "date"));
        propertiesMap.put("date", Map.of("type", "date", "format", "strict_date"));
        propertiesMap.put("organizerId", Map.of("type", "long"));
        propertiesMap.put("organizerName", searchAsYouType);
        propertiesMap.put("organizerApiKey", keyword);
        propertiesMap.put("organizerVerificationMode", keyword);
        propertiesMap.put("venueId", Map.of("type", "long"));
        propertiesMap.put("venueName", searchAsYouType);
        propertiesMap.put("venueAddress", text);
        propertiesMap.put("venueTimezone", keyword);

        return Map.of(
                "settings", Map.of(
                        "analysis", Map.of(
                                "analyzer", Map.of(
                                        "default", Map.of("type", "standard")
                                )
                        )
                ),
                "mappings", Map.of("properties", propertiesMap)
        );
    }

    private Map<String, Object> searchBody(String query, Long organizerId, int limit) {
        List<Object> must = new ArrayList<>();
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            must.add(Map.of("match_all", Map.of()));
        } else {
            must.add(Map.of(
                    "multi_match",
                    Map.of(
                            "query", normalizedQuery,
                            "type", "bool_prefix",
                            "fields", List.of(
                                    "name^4",
                                    "name._2gram^3",
                                    "name._3gram^3",
                                    "organizerName^2",
                                    "organizerName._2gram",
                                    "organizerName._3gram",
                                    "venueName",
                                    "venueName._2gram",
                                    "venueName._3gram",
                                    "venueAddress",
                                    "eventId"
                            )
                    )
            ));
        }

        List<Object> filter = new ArrayList<>();
        filter.add(Map.of("range", Map.of("startsAt", Map.of("gte", clock.instant().toString()))));
        if (organizerId != null) {
            filter.add(Map.of("term", Map.of("organizerId", organizerId)));
        }

        return Map.of(
                "size", limit,
                "query", Map.of(
                        "bool", Map.of(
                                "must", must,
                                "filter", filter
                        )
                ),
                "sort", List.of(Map.of("startsAt", Map.of("order", "asc")))
        );
    }

    private Map<String, Object> toDocument(Event event) {
        Organizer organizer = event.getOrganizer();
        Venue venue = event.getVenue();
        return Map.ofEntries(
                Map.entry("id", event.getId()),
                Map.entry("eventId", safe(event.getEventId())),
                Map.entry("name", safe(event.getName())),
                Map.entry("startsAt", event.getStartsAt().toString()),
                Map.entry("date", event.getDate().toString()),
                Map.entry("organizerId", organizer.getId()),
                Map.entry("organizerName", safe(organizer.getName())),
                Map.entry("organizerApiKey", safe(organizer.getApiKey())),
                Map.entry("organizerVerificationMode", organizer.getVerificationMode().name()),
                Map.entry("venueId", venue.getId()),
                Map.entry("venueName", safe(venue.getName())),
                Map.entry("venueAddress", safe(venue.getAddress())),
                Map.entry("venueTimezone", safe(venue.getTimezone()))
        );
    }

    private EventSearchResponse fromDocument(Map<String, Object> source) {
        try {
            Long id = asLong(source.get("id"));
            Long organizerId = asLong(source.get("organizerId"));
            Long venueId = asLong(source.get("venueId"));
            String eventId = asString(source.get("eventId"));
            String name = asString(source.get("name"));
            Instant startsAt = Instant.parse(asString(source.get("startsAt")));
            String dateText = asString(source.get("date"));
            String organizerName = asString(source.get("organizerName"));
            String organizerApiKey = asString(source.get("organizerApiKey"));
            String organizerVerificationMode = asString(source.get("organizerVerificationMode"));
            String venueName = asString(source.get("venueName"));
            String venueAddress = asString(source.get("venueAddress"));
            String venueTimezone = asString(source.get("venueTimezone"));

            return new EventSearchResponse(
                    id,
                    eventId,
                    name,
                    startsAt,
                    java.time.LocalDate.parse(dateText),
                    new EventSearchResponse.OrganizerInfo(organizerId, organizerName, organizerApiKey, organizerVerificationMode),
                    new EventSearchResponse.VenueInfo(venueId, venueName, venueAddress, venueTimezone),
                    new EventSearchResponse.ListingDefaults(
                            id,
                            name,
                            startsAt,
                            venueName + ", " + venueAddress,
                            organizerId,
                            organizerName,
                            eventId
                    )
            );
        } catch (RuntimeException ex) {
            log.warn("Failed to map Elasticsearch event document: {}", source, ex);
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return null;
    }

    private String asString(Object value) {
        return Objects.toString(value, "");
    }
}
