package ru.ticketswap.common;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError of(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }

    public static ApiError fromValidation(MethodArgumentNotValidException ex, String path) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            if (!fields.containsKey(fe.getField())) {
                fields.put(fe.getField(), fe.getDefaultMessage());
            }
        }

        if (fields.isEmpty()) {
            return ApiError.of(400, "Некорректный запрос", "Ошибка валидации", path);
        }
        return ApiError.of(400, "Некорректный запрос", "Ошибка валидации", path, fields);
    }
}