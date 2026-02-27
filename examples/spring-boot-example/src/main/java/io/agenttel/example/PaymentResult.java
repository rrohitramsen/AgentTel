package io.agenttel.example;

public record PaymentResult(String transactionId, String status, double amount) {
}
