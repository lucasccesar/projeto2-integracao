package com.example.orders.application;

import com.example.orders.api.OrderResponse;
import com.example.orders.api.PaymentWebhookRequest;
import com.example.orders.api.ProcessWebhookResponse;
import com.example.orders.api.ProcessedWebhookResponse;
import com.example.orders.persistence.OrderRepository;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private static final String APPROVED_EVENT = "payment.approved";

    private final OrderRepository orderRepository;

    public WebhookService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public ProcessWebhookResponse process(PaymentWebhookRequest event) {
        if (!APPROVED_EVENT.equals(event.eventType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported event type");
        }

        if (orderRepository.claimWebhook(event) == 0) {
            ProcessedWebhookResponse previous = orderRepository.findWebhook(event.eventId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "Event ID is already claimed with an unreadable payload"));
            if (!samePayload(previous, event)) {
                logger.warn("Conflicting duplicate webhook eventId={} orderId={}", event.eventId(), event.orderId());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Event ID was already used with a different payload");
            }
            return respond(event, "ALREADY_PROCESSED");
        }

        OrderResponse order = orderRepository.lockOrder(event.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.amountCents() != event.amountCents()) {
            logger.warn("Webhook amount mismatch eventId={} orderId={}", event.eventId(), event.orderId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event amount does not match order amount");
        }

        if ("PAID".equals(order.status())) {
            return respond(event, "ALREADY_PAID");
        }

        if (orderRepository.markPaid(event.orderId()) != 1) {
            throw new IllegalStateException("Locked pending order could not be marked paid");
        }
        return respond(event, "PROCESSED");
    }

    public List<ProcessedWebhookResponse> findRecentEvents() {
        return orderRepository.findRecentWebhooks();
    }

    private boolean samePayload(ProcessedWebhookResponse previous, PaymentWebhookRequest event) {
        return Objects.equals(previous.eventType(), event.eventType())
                && Objects.equals(previous.orderId(), event.orderId())
                && previous.amountCents() == event.amountCents();
    }

    private ProcessWebhookResponse respond(PaymentWebhookRequest event, String result) {
        logger.info("Webhook eventId={} orderId={} result={}", event.eventId(), event.orderId(), result);
        return new ProcessWebhookResponse(event.eventId(), result);
    }
}
