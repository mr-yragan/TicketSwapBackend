package ru.ticketswap.ticket.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record TicketLotPageResponse(
        List<TicketLotResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        int numberOfElements,
        boolean first,
        boolean last,
        boolean empty,
        String sort
) {

    public static TicketLotPageResponse from(Page<?> page, List<TicketLotResponse> content, String sort) {
        return new TicketLotPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumberOfElements(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty(),
                sort == null || sort.isBlank() ? "createdDesc" : sort.trim()
        );
    }
}
