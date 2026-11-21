package com.example.payments.api;

public record PaymentWebhook(String eventId, String eventType, String orderId, long amountCents) {
}
