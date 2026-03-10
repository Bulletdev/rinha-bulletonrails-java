package com.bulletonrails.rinha.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentProcessorRequest(
    @JsonProperty("correlationId") UUID correlationId,
    @JsonProperty("amount") BigDecimal amount,
    @JsonProperty("requestedAt") Instant requestedAt
) {
}
