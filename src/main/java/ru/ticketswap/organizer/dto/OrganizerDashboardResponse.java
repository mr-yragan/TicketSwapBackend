package ru.ticketswap.organizer.dto;

public record OrganizerDashboardResponse(
        Long organizerId,
        String name,
        String apiKey,
        String contactEmail,
        long eventsCount,
        boolean mockMode
) {
}
