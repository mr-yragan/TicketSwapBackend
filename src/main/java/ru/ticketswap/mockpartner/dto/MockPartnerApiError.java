package ru.ticketswap.mockpartner.dto;

public record MockPartnerApiError(
        String error,
        String message,
        int status
) {

    public static MockPartnerApiError badRequest(String message) {
        return new MockPartnerApiError("Некорректный запрос", message, 400);
    }

    public static MockPartnerApiError notFound(String message) {
        return new MockPartnerApiError("Не найдено", message, 404);
    }
}