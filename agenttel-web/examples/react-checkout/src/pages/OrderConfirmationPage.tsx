import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

interface OrderDetails {
  orderId: string;
  total: number;
  items: number;
  transactionId?: string;
}

export const OrderConfirmationPage: React.FC = () => {
  const [order, setOrder] = useState<OrderDetails | null>(null);

  useEffect(() => {
    const saved = sessionStorage.getItem('lastOrder');
    if (saved) {
      setOrder(JSON.parse(saved));
    }
  }, []);

  if (!order) {
    return (
      <div style={styles.empty}>
        <h2 style={styles.title}>No Order Found</h2>
        <p style={styles.subtitle}>Start shopping to place an order.</p>
        <Link to="/products" style={styles.link}>Browse Products</Link>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      <div style={styles.successIcon}>✓</div>
      <h1 style={styles.title}>Order Confirmed!</h1>
      <p style={styles.subtitle}>Thank you for your purchase</p>

      <div style={styles.card}>
        <div style={styles.row}>
          <span style={styles.label}>Order ID</span>
          <span style={styles.value}>{order.orderId}</span>
        </div>
        {order.transactionId && (
          <div style={styles.row}>
            <span style={styles.label}>Transaction</span>
            <span style={styles.value}>{order.transactionId}</span>
          </div>
        )}
        <div style={styles.row}>
          <span style={styles.label}>Items</span>
          <span style={styles.value}>{order.items}</span>
        </div>
        <div style={styles.divider} />
        <div style={styles.row}>
          <span style={styles.label}>Total Charged</span>
          <span style={styles.totalValue}>${order.total.toFixed(2)}</span>
        </div>
      </div>

      <div style={styles.traceInfo}>
        <h3 style={styles.traceTitle}>Trace Correlation</h3>
        <p style={styles.traceText}>
          This checkout journey generated a unified trace spanning the browser and backend.
          View it in{' '}
          <a href="http://localhost:16686" target="_blank" rel="noreferrer" style={styles.link}>
            Jaeger
          </a>{' '}
          — search for service <code style={styles.code}>checkout-web</code> to see the full
          frontend-to-backend trace tree.
        </p>
      </div>

      <Link to="/products" style={styles.continueLink}>
        Continue Shopping
      </Link>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    textAlign: 'center' as const,
    maxWidth: '520px',
    margin: '0 auto',
  },
  empty: {
    textAlign: 'center' as const,
    padding: '64px 0',
  },
  successIcon: {
    width: '64px',
    height: '64px',
    borderRadius: '50%',
    background: 'linear-gradient(135deg, #22c55e 0%, #16a34a 100%)',
    color: '#fff',
    fontSize: '32px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    margin: '0 auto 20px',
    fontWeight: 700,
  },
  title: {
    fontSize: '28px',
    fontWeight: 700,
    color: '#e0e7ff',
    marginBottom: '8px',
  },
  subtitle: {
    fontSize: '16px',
    color: '#94a3b8',
    marginBottom: '32px',
  },
  card: {
    background: '#1e1b4b',
    borderRadius: '12px',
    padding: '24px',
    border: '1px solid #312e81',
    textAlign: 'left' as const,
    marginBottom: '24px',
  },
  row: {
    display: 'flex',
    justifyContent: 'space-between',
    padding: '10px 0',
  },
  label: {
    color: '#94a3b8',
    fontSize: '14px',
  },
  value: {
    color: '#e0e7ff',
    fontWeight: 600,
    fontSize: '14px',
    fontFamily: 'monospace',
  },
  totalValue: {
    color: '#a5b4fc',
    fontWeight: 700,
    fontSize: '20px',
  },
  divider: {
    height: '1px',
    background: '#312e81',
    margin: '8px 0',
  },
  traceInfo: {
    background: 'rgba(99, 102, 241, 0.1)',
    borderRadius: '12px',
    padding: '20px',
    border: '1px solid rgba(99, 102, 241, 0.2)',
    textAlign: 'left' as const,
    marginBottom: '24px',
  },
  traceTitle: {
    color: '#a5b4fc',
    fontSize: '14px',
    fontWeight: 600,
    marginBottom: '8px',
  },
  traceText: {
    color: '#94a3b8',
    fontSize: '13px',
    lineHeight: 1.6,
  },
  link: {
    color: '#818cf8',
  },
  code: {
    background: 'rgba(99, 102, 241, 0.2)',
    padding: '2px 6px',
    borderRadius: '4px',
    fontSize: '12px',
    color: '#a5b4fc',
  },
  continueLink: {
    display: 'inline-block',
    color: '#818cf8',
    fontSize: '16px',
    textDecoration: 'none',
    padding: '12px 24px',
    border: '1px solid #4338ca',
    borderRadius: '10px',
  },
};
