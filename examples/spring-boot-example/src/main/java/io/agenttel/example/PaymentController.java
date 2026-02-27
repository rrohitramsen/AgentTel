package io.agenttel.example;

import io.agenttel.api.annotations.AgentOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Tag(name = "Payments", description = "Payment processing operations")
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Operation(
            summary = "Process a payment",
            description = "Processes a payment transaction. AgentTel profile: critical-write "
                    + "(escalation=PAGE_ONCALL, safeToRestart=false). Baselines and decision "
                    + "context are configured in application.yml."
    )
    @ApiResponse(responseCode = "200", description = "Payment processed successfully")
    @AgentOperation(profile = "critical-write")
    @PostMapping
    public ResponseEntity<PaymentResult> processPayment(@RequestBody PaymentRequest request) {
        // Simulate processing latency
        simulateLatency(30, 80);
        String txnId = "txn-" + UUID.randomUUID().toString().substring(0, 8);
        return ResponseEntity.ok(new PaymentResult(txnId, "SUCCESS", request.amount()));
    }

    @Operation(
            summary = "Get payment by ID",
            description = "Retrieves payment details by transaction ID. AgentTel profile: read-only "
                    + "(retryable=true, idempotent=true)."
    )
    @ApiResponse(responseCode = "200", description = "Payment details retrieved")
    @AgentOperation(profile = "read-only")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResult> getPayment(
            @Parameter(description = "Payment transaction ID", example = "txn-a1b2c3d4")
            @PathVariable String id) {
        simulateLatency(5, 20);
        return ResponseEntity.ok(new PaymentResult(id, "COMPLETED", 100.0));
    }

    private void simulateLatency(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
