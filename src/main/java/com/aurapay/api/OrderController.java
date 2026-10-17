package com.aurapay.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final Map<String, DemoOrder> demoOrders = new ConcurrentHashMap<>();
    private final CreditPackController creditPackController = new CreditPackController();

    @PostMapping
    public ResponseEntity<?> createDemoOrder(@RequestBody CreateOrderRequest request) {
        if (request == null || request.packId() == null || request.packId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "packId is required"));
        }

        CreditPackController.CreditPack pack = creditPackController.listCreditPacks().stream()
                .filter(item -> item.id().equals(request.packId()))
                .findFirst()
                .orElse(null);

        if (pack == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown credit pack"));
        }

        String orderId = UUID.randomUUID().toString();
        DemoOrder order = new DemoOrder(
                orderId,
                pack.id(),
                pack.name(),
                pack.credits(),
                pack.amountInPaise(),
                "DEMO_CREATED",
                false,
                Instant.now().toString()
        );
        demoOrders.put(orderId, order);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getDemoOrder(@PathVariable String orderId) {
        DemoOrder order = demoOrders.get(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Demo order not found; orders are held in memory and may be lost on restart"));
        }
        return ResponseEntity.ok(order);
    }

    public record CreateOrderRequest(String packId) {}

    public record DemoOrder(
            String orderId,
            String packId,
            String packName,
            int credits,
            long amountInPaise,
            String status,
            boolean realPaymentsEnabled,
            String createdAt
    ) {}
}
