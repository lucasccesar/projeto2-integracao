package com.example.payments.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ApprovePaymentRequest(
        @NotBlank String orderId,
        @NotNull @Positive Long amountCents
) {
}
