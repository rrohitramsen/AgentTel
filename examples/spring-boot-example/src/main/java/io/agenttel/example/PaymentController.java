package io.agenttel.example;

import io.agenttel.api.EscalationLevel;
import io.agenttel.api.annotations.AgentOperation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @AgentOperation(
            expectedLatencyP50 = "45ms",
            expectedLatencyP99 = "200ms",
            expectedErrorRate = 0.001,
            retryable = true,
            idempotent = true,
            runbookUrl = "https://wiki/runbooks/process-payment",
            fallbackDescription = "Returns cached pricing from last successful gateway call",
            escalationLevel = EscalationLevel.PAGE_ONCALL,
            safeToRestart = false
    )
    @PostMapping
    public ResponseEntity<PaymentResult> processPayment(@RequestBody PaymentRequest request) {
        // Simulate processing latency
        simulateLatency(30, 80);
        String txnId = "txn-" + UUID.randomUUID().toString().substring(0, 8);
        return ResponseEntity.ok(new PaymentResult(txnId, "SUCCESS", request.amount()));
    }

    @AgentOperation(
            expectedLatencyP50 = "10ms",
            expectedLatencyP99 = "50ms",
            retryable = true,
            idempotent = true
    )
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResult> getPayment(@PathVariable String id) {
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
