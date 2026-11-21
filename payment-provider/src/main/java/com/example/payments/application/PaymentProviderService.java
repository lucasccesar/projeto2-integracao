package com.example.payments.application;

import com.example.payments.api.ApprovePaymentRequest;
import com.example.payments.api.DeliveryResponse;
import com.example.payments.api.PaymentWebhook;
import com.example.payments.api.ReceiverResponse;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PaymentProviderService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentProviderService.class);
    private static final String APPROVED_EVENT = "payment.approved";

    private final ConcurrentMap<String, StoredEvent> events = new ConcurrentHashMap<>();
    private final RestClient restClient;
    private final String receiverUrl;

    public PaymentProviderService(
            RestClient restClient,
            @Value("${provider.receiver-url}") String receiverUrl) {
        this.restClient = restClient;
        this.receiverUrl = receiverUrl;
    }

    public DeliveryResponse approve(ApprovePaymentRequest request) {
        String eventId = "evt-" + UUID.randomUUID();
        PaymentWebhook webhook = new PaymentWebhook(
                eventId, APPROVED_EVENT, request.orderId(), request.amountCents());
        events.put(eventId, new StoredEvent(webhook, "PENDING"));
        logger.info("Created payment event eventId={} orderId={}", eventId, request.orderId());
        return deliver(eventId, webhook);
    }

    public DeliveryResponse redeliver(String eventId) {
        StoredEvent storedEvent = events.get(eventId);
        if (storedEvent == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider event not found");
        }
        logger.info("Redelivering payment event eventId={} orderId={}", eventId, storedEvent.webhook().orderId());
        return deliver(eventId, storedEvent.webhook());
    }

    private DeliveryResponse deliver(String eventId, PaymentWebhook webhook) {
        logger.info("Sending webhook over HTTP eventId={} orderId={} receiver={}",
                eventId, webhook.orderId(), receiverUrl);
        DeliveryResponse outcome;
        try {
            ResponseEntity<ReceiverResponse> response = restClient.post()
                    .uri(receiverUrl)
                    .body(webhook)
                    .retrieve()
                    .toEntity(ReceiverResponse.class);
            ReceiverResponse receiverResponse = response.getBody();
            outcome = new DeliveryResponse(
                    eventId,
                    webhook.orderId(),
                    "DELIVERED",
                    receiverResponse == null ? null : receiverResponse.result(),
                    response.getStatusCode().value());
            logger.info("Webhook delivery eventId={} orderId={} deliveryOutcome={} receiverResult={}",
                    eventId, webhook.orderId(), outcome.deliveryOutcome(), outcome.receiverResult());
        } catch (RestClientResponseException exception) {
            outcome = new DeliveryResponse(
                    eventId, webhook.orderId(), "RECEIVER_REJECTED", null, exception.getStatusCode().value());
            logger.warn("Webhook receiver rejected eventId={} orderId={} httpStatus={}",
                    eventId, webhook.orderId(), exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            outcome = new DeliveryResponse(eventId, webhook.orderId(), "DELIVERY_FAILED", null, null);
            logger.warn("Webhook delivery eventId={} orderId={} result=DELIVERY_FAILED reason={}",
                    eventId, webhook.orderId(), exception.getMostSpecificCause().getClass().getSimpleName());
        }
        String deliveryOutcome = outcome.deliveryOutcome();
        events.computeIfPresent(eventId, (key, current) -> new StoredEvent(webhook, deliveryOutcome));
        return outcome;
    }

    private record StoredEvent(PaymentWebhook webhook, String lastDeliveryOutcome) {
    }
}
