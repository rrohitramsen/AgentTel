package io.agenttel.example;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment processing result")
public record PaymentResult(
        @Schema(description = "Unique transaction identifier", example = "txn-a1b2c3d4")
        String transactionId,
        @Schema(description = "Transaction status", example = "SUCCESS")
        String status,
        @Schema(description = "Processed amount", example = "99.99")
        double amount) {
}
