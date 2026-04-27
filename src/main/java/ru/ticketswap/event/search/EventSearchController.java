package ru.ticketswap.event.search;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.event.search.dto.EventSearchResponse;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventSearchController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 25;

    private final EventSearchService eventSearchService;

    public EventSearchController(EventSearchService eventSearchService) {
        this.eventSearchService = eventSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<EventSearchResponse>> searchEvents(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "organizerId", required = false) Long organizerId,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        int normalizedLimit = normalizeLimit(limit);
        String normalizedQuery = normalizeQuery(query, q);
        return ResponseEntity.ok(eventSearchService.searchUpcomingEvents(normalizedQuery, organizerId, normalizedLimit));
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            throw new BusinessRuleException("limit должен быть положительным");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeQuery(String query, String q) {
        String value = query;
        if ((value == null || value.isBlank()) && q != null) {
            value = q;
        }
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
