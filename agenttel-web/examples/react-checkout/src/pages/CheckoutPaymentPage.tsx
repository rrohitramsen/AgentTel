import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PaymentForm, PaymentData } from '../components/PaymentForm';
import { submitPayment } from '../api/payments';
import { Product } from '../components/ProductCard';

export const CheckoutPaymentPage: React.FC = () => {
  const navigate = useNavigate();
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const cart: Product[] = (() => {
    const saved = sessionStorage.getItem('cart');
    return saved ? JSON.parse(saved) : [];
  })();

  const total = cart.reduce((sum: number, p: Product) => sum + p.price, 0);

  const handlePayment = async (_data: PaymentData) => {
    setIsProcessing(true);
    setError(null);

    try {
      const orderId = `ORD-${Date.now()}`;

      // This fetch call is automatically instrumented by AgentTel Web SDK:
      // - traceparent header injected for cross-stack correlation
      // - API span created with timing, status, and correlation attributes
      const result = await submitPayment({
        amount: total,
        currency: 'USD',
        cardLast4: '4242',
        orderId,
      });

      if (result.status === 'success') {
        sessionStorage.setItem('lastOrder', JSON.stringify({
          orderId,
          total,
          items: cart.length,
          transactionId: result.transactionId,
        }));
        sessionStorage.removeItem('cart');
        navigate('/order/confirmation');
      } else {
        setError(result.message || 'Payment declined. Please try again.');
      }
    } catch (err) {
      // The AgentTel Web SDK captures this as an error span
      setError(err instanceof Error ? err.message : 'Payment failed. Please try again.');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div>
      <h1 style={styles.title}>Payment</h1>
      <p style={styles.subtitle}>Complete your purchase</p>

      <div style={styles.layout}>
        <div style={styles.formSection}>
          {error && (
            <div style={styles.error}>
              <strong>Payment Error:</strong> {error}
            </div>
          )}
          <PaymentForm onSubmit={handlePayment} isProcessing={isProcessing} />
        </div>

        <div style={styles.summarySection}>
          <h3 style={styles.summaryTitle}>Order Summary</h3>
          {cart.map((item: Product, i: number) => (
            <div key={`${item.id}-${i}`} style={styles.summaryItem}>
              <span>{item.image} {item.name}</span>
              <span style={styles.summaryPrice}>${item.price.toFixed(2)}</span>
            </div>
          ))}
          <div style={styles.divider} />
          <div style={styles.summaryTotal}>
            <span>Total</span>
            <span>${total.toFixed(2)}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  title: {
    fontSize: '28px',
    fontWeight: 700,
    color: '#e0e7ff',
    marginBottom: '4px',
  },
  subtitle: {
    fontSize: '16px',
    color: '#94a3b8',
    marginBottom: '32px',
  },
  layout: {
    display: 'grid',
    gridTemplateColumns: '1fr 320px',
    gap: '32px',
    alignItems: 'start',
  },
  formSection: {
    display: 'flex',
    flexDirection: 'column',
    gap: '20px',
  },
  error: {
    background: 'rgba(239, 68, 68, 0.1)',
    border: '1px solid #dc2626',
    color: '#fca5a5',
    padding: '12px 16px',
    borderRadius: '8px',
    fontSize: '14px',
  },
  summarySection: {
    background: '#1e1b4b',
    borderRadius: '12px',
    padding: '24px',
    border: '1px solid #312e81',
  },
  summaryTitle: {
    fontSize: '16px',
    fontWeight: 600,
    color: '#e0e7ff',
    marginBottom: '16px',
  },
  summaryItem: {
    display: 'flex',
    justifyContent: 'space-between',
    padding: '8px 0',
    fontSize: '14px',
    color: '#94a3b8',
  },
  summaryPrice: {
    color: '#a5b4fc',
    fontWeight: 600,
  },
  divider: {
    height: '1px',
    background: '#312e81',
    margin: '12px 0',
  },
  summaryTotal: {
    display: 'flex',
    justifyContent: 'space-between',
    fontSize: '18px',
    fontWeight: 700,
    color: '#e0e7ff',
  },
};
