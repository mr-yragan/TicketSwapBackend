package ru.ticketswap.mockpartner.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.ticketswap.mockpartner.controller.MockPartnerController;
import ru.ticketswap.mockpartner.dto.MockPartnerApiError;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = MockPartnerController.class)
public class MockPartnerExceptionHandler {

    @ExceptionHandler(MockPartnerOrganizerNotFoundException.class)
    public ResponseEntity<MockPartnerApiError> handleOrganizerNotFound(MockPartnerOrganizerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MockPartnerApiError.notFound(ex.getMessage()));
    }

    @ExceptionHandler(MockPartnerBadRequestException.class)
    public ResponseEntity<MockPartnerApiError> handleBadRequest(MockPartnerBadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(MockPartnerApiError.badRequest(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MockPartnerApiError> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(MockPartnerApiError.badRequest("Malformed JSON request"));
    }
}
