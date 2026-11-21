package com.example.orders.api;

import java.time.OffsetDateTime;

public record OrderResponse(
        String orderId,
        long amountCents,
        String status,
        int paymentApplications,
        OffsetDateTime paidAt
) {
}
