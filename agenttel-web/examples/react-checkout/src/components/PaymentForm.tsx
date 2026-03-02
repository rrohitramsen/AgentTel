import React, { useState } from 'react';

interface PaymentFormProps {
  onSubmit: (data: PaymentData) => void;
  isProcessing: boolean;
}

export interface PaymentData {
  cardNumber: string;
  expiry: string;
  cvv: string;
  nameOnCard: string;
}

export const PaymentForm: React.FC<PaymentFormProps> = ({ onSubmit, isProcessing }) => {
  const [form, setForm] = useState<PaymentData>({
    cardNumber: '4242 4242 4242 4242',
    expiry: '12/28',
    cvv: '123',
    nameOnCard: 'Jane Doe',
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit(form);
  };

  const update = (field: keyof PaymentData, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  return (
    <form onSubmit={handleSubmit} style={styles.form} data-agenttel-target="payment-form">
      <div style={styles.field}>
        <label style={styles.label}>Name on Card</label>
        <input
          style={styles.input}
          value={form.nameOnCard}
          onChange={(e) => update('nameOnCard', e.target.value)}
          placeholder="Jane Doe"
          required
        />
      </div>

      <div style={styles.field}>
        <label style={styles.label}>Card Number</label>
        <input
          style={styles.input}
          value={form.cardNumber}
          onChange={(e) => update('cardNumber', e.target.value)}
          placeholder="4242 4242 4242 4242"
          required
        />
      </div>

      <div style={styles.row}>
        <div style={styles.field}>
          <label style={styles.label}>Expiry</label>
          <input
            style={styles.input}
            value={form.expiry}
            onChange={(e) => update('expiry', e.target.value)}
            placeholder="MM/YY"
            required
          />
        </div>
        <div style={styles.field}>
          <label style={styles.label}>CVV</label>
          <input
            style={styles.input}
            value={form.cvv}
            onChange={(e) => update('cvv', e.target.value)}
            placeholder="123"
            type="password"
            required
          />
        </div>
      </div>

      <button
        type="submit"
        style={{
          ...styles.button,
          ...(isProcessing ? styles.buttonDisabled : {}),
        }}
        disabled={isProcessing}
        data-agenttel-target="submit-payment"
      >
        {isProcessing ? 'Processing...' : 'Pay Now'}
      </button>
    </form>
  );
};

const styles: Record<string, React.CSSProperties> = {
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '20px',
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
  buttonDisabled: {
    opacity: 0.6,
    cursor: 'not-allowed',
  },
};
