package ru.ticketswap.organizer.dto;

public record OrganizerDashboardResponse(
        Long organizerId,
        String email,
        String role,
        boolean mockMode,
        long eventsCount,
        String message
) {
}
