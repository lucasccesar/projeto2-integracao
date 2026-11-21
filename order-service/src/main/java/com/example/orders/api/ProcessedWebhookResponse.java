package com.example.orders.api;

import java.time.OffsetDateTime;

public record ProcessedWebhookResponse(
        String eventId,
        String eventType,
        String orderId,
        long amountCents,
        OffsetDateTime processedAt
) {
}
