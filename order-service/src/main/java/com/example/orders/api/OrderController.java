package com.example.orders.api;

import com.example.orders.application.OrderService;
import com.example.orders.application.WebhookService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class OrderController {

    private final OrderService orderService;
    private final WebhookService webhookService;

    public OrderController(OrderService orderService, WebhookService webhookService) {
        this.orderService = orderService;
        this.webhookService = webhookService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable String orderId) {
        return orderService.findOrder(orderId);
    }

    @PostMapping("/webhooks/payments")
    public ProcessWebhookResponse receivePayment(@Valid @RequestBody PaymentWebhookRequest event) {
        return webhookService.process(event);
    }

    @GetMapping("/webhook-events")
    public List<ProcessedWebhookResponse> getWebhookEvents() {
        return webhookService.findRecentEvents();
    }
}
