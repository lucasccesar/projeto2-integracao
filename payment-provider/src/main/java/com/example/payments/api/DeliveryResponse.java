package com.example.payments.api;

public record DeliveryResponse(
        String eventId,
        String orderId,
        String deliveryOutcome,
        String receiverResult,
        Integer receiverHttpStatus
) {
}
