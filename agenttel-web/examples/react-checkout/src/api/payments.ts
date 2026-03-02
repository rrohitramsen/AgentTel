export interface PaymentRequest {
  amount: number;
  currency: string;
  cardLast4: string;
  orderId: string;
}

export interface PaymentResponse {
  id: string;
  status: 'success' | 'failed';
  message: string;
  transactionId?: string;
}

/**
 * Submits a payment to the backend payment-service.
 *
 * The AgentTel Web SDK automatically:
 * 1. Injects the `traceparent` header for cross-stack correlation
 * 2. Creates an API call span with timing and status
 * 3. Extracts the backend's `traceparent` from the response
 */
export async function submitPayment(request: PaymentRequest): Promise<PaymentResponse> {
  const response = await fetch('/api/payments', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const error = await response.text().catch(() => 'Payment failed');
    throw new Error(error);
  }

  return response.json();
}

/**
 * Fetches the order summary after a successful payment.
 */
export async function getOrderSummary(orderId: string): Promise<{
  orderId: string;
  total: number;
  items: number;
  status: string;
}> {
  const response = await fetch(`/api/orders/${orderId}`);

  if (!response.ok) {
    // Return a mock order summary for the demo
    return {
      orderId,
      total: 99.99,
      items: 3,
      status: 'confirmed',
    };
  }

  return response.json();
}
