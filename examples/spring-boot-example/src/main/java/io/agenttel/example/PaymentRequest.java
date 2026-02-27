package io.agenttel.example;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment processing request")
public record PaymentRequest(
        @Schema(description = "Payment amount in the specified currency", example = "99.99")
        double amount,
        @Schema(description = "Currency code (ISO 4217)", example = "USD")
        String currency) {
}
