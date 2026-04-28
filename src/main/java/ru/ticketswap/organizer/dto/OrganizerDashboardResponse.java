package ru.ticketswap.organizer.dto;

public record OrganizerDashboardResponse(
        Long organizerId,
        String organizerName,
        String organizerCode,
        long pendingValidationCount,
        long pendingReissueCount
) {
}
