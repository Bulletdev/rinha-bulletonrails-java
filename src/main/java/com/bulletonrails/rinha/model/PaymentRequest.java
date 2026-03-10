package com.bulletonrails.rinha.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRequest(
    @JsonProperty("correlationId") UUID correlationId,
    @JsonProperty("amount") BigDecimal amount
) {
    public PaymentProcessorRequest toProcessorRequest() {
        return new PaymentProcessorRequest(
            correlationId, amount, Instant.now());
    }
}
