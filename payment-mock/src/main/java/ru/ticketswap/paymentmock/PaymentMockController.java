package ru.ticketswap.paymentmock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/mock/payments")
public class PaymentMockController {

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizeResponse> authorize(@Valid @RequestBody AuthorizeRequest request) {
        String basis = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? request.orderReference()
                : request.idempotencyKey();
        boolean forceOk = basis.toLowerCase().contains("force-ok");
        boolean forceFail = basis.toLowerCase().contains("force-fail") || request.orderReference().toLowerCase().contains("force-fail");
        boolean authorized = forceOk || (!forceFail && Math.floorMod(basis.hashCode(), 10) != 0);
        if (!authorized) {
            return ResponseEntity.ok(new AuthorizeResponse(false, null, "Mock payment declined"));
        }
        return ResponseEntity.ok(new AuthorizeResponse(true, "PAY-" + Math.floorMod(basis.hashCode(), Integer.MAX_VALUE) + "-" + System.currentTimeMillis(), null));
    }

    @PostMapping("/capture")
    public ResponseEntity<CaptureResponse> capture(@Valid @RequestBody CaptureRequest request) {
        if (request.paymentOperationId().toUpperCase().contains("CAPTURE_FAIL")) {
            return ResponseEntity.ok(new CaptureResponse(false, request.paymentOperationId(), "Mock capture failed"));
        }
        return ResponseEntity.ok(new CaptureResponse(true, request.paymentOperationId(), null));
    }

    @PostMapping("/refund")
    public ResponseEntity<RefundResponse> refund(@Valid @RequestBody RefundRequest request) {
        if (request.paymentOperationId().toUpperCase().contains("REFUND_FAIL")) {
            return ResponseEntity.ok(new RefundResponse(false, request.paymentOperationId(), "Mock refund failed"));
        }
        return ResponseEntity.ok(new RefundResponse(true, request.paymentOperationId(), null));
    }

    public record AuthorizeRequest(@NotBlank String orderReference, @NotNull BigDecimal amount, @NotBlank String currency, @NotBlank String buyerEmail, String idempotencyKey) {}
    public record AuthorizeResponse(boolean authorized, String paymentOperationId, String reason) {}
    public record CaptureRequest(@NotBlank String paymentOperationId) {}
    public record CaptureResponse(boolean captured, String paymentOperationId, String reason) {}
    public record RefundRequest(@NotBlank String paymentOperationId, String reason) {}
    public record RefundResponse(boolean refunded, String paymentOperationId, String reason) {}
}
