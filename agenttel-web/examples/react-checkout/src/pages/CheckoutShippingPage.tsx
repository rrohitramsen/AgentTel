import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export const CheckoutShippingPage: React.FC = () => {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    fullName: 'Jane Doe',
    address: '123 Observability Lane',
    city: 'San Francisco',
    state: 'CA',
    zip: '94105',
    country: 'US',
  });

  const update = (field: string, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    sessionStorage.setItem('shipping', JSON.stringify(form));
    navigate('/checkout/payment');
  };

  return (
    <div>
      <h1 style={styles.title}>Shipping Address</h1>
      <p style={styles.subtitle}>Where should we send your order?</p>

      <form onSubmit={handleSubmit} style={styles.form} data-agenttel-target="shipping-form">
        <div style={styles.field}>
          <label style={styles.label}>Full Name</label>
          <input
            style={styles.input}
            value={form.fullName}
            onChange={(e) => update('fullName', e.target.value)}
            required
          />
        </div>

        <div style={styles.field}>
          <label style={styles.label}>Street Address</label>
          <input
            style={styles.input}
            value={form.address}
            onChange={(e) => update('address', e.target.value)}
            required
          />
        </div>

        <div style={styles.row}>
          <div style={styles.field}>
            <label style={styles.label}>City</label>
            <input
              style={styles.input}
              value={form.city}
              onChange={(e) => update('city', e.target.value)}
              required
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>State</label>
            <input
              style={styles.input}
              value={form.state}
              onChange={(e) => update('state', e.target.value)}
              required
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>ZIP Code</label>
            <input
              style={styles.input}
              value={form.zip}
              onChange={(e) => update('zip', e.target.value)}
              required
            />
          </div>
        </div>

        <button type="submit" style={styles.button} data-agenttel-target="continue-to-payment">
          Continue to Payment
        </button>
      </form>
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
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '20px',
    maxWidth: '560px',
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
    flex: 1,
  },
  label: {
    fontSize: '13px',
    fontWeight: 600,
    color: '#a5b4fc',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
  },
  input: {
    background: '#0f0a1e',
    border: '1px solid #312e81',
    borderRadius: '8px',
    padding: '12px 16px',
    fontSize: '16px',
    color: '#e0e7ff',
    outline: 'none',
  },
  row: {
    display: 'flex',
    gap: '16px',
  },
  button: {
    background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
    color: '#fff',
    border: 'none',
    padding: '16px',
    borderRadius: '10px',
    fontSize: '16px',
    fontWeight: 700,
    cursor: 'pointer',
    marginTop: '8px',
  },
};
