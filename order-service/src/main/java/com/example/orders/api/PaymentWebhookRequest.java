package com.example.orders.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentWebhookRequest(
        @NotBlank String eventId,
        @NotBlank String eventType,
        @NotBlank String orderId,
        @NotNull @Positive Long amountCents
) {
}
