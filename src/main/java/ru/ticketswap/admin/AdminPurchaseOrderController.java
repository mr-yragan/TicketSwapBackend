package ru.ticketswap.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.purchase.PurchaseOrderRepository;
import ru.ticketswap.purchase.dto.PurchaseOrderResponse;

import java.util.List;

@RestController
@RequestMapping("/api/admin/purchase-orders")
public class AdminPurchaseOrderController {

    private final PurchaseOrderRepository purchaseOrderRepository;

    public AdminPurchaseOrderController(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrderResponse>> listPurchaseOrders() {
        List<PurchaseOrderResponse> response = purchaseOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(order -> PurchaseOrderResponse.fromEntity(order, true))
                .toList();
        return ResponseEntity.ok(response);
    }
}
