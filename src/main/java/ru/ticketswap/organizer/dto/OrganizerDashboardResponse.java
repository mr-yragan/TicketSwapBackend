package ru.ticketswap.organizer.dto;

import ru.ticketswap.organizer.OrganizerVerificationMode;

public record OrganizerDashboardResponse(
        Long organizerId,
        String name,
        String apiKey,
        String contactEmail,
        OrganizerVerificationMode verificationMode,
        boolean banned,
        long eventsCount,
        long pendingValidationCount,
        long pendingReissueCount,
        boolean mockMode
) {
}
