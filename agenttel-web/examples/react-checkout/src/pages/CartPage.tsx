import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Product } from '../components/ProductCard';

export const CartPage: React.FC = () => {
  const navigate = useNavigate();
  const [cart, setCart] = useState<Product[]>(() => {
    const saved = sessionStorage.getItem('cart');
    return saved ? JSON.parse(saved) : [];
  });

  const removeItem = (index: number) => {
    const updated = cart.filter((_, i) => i !== index);
    setCart(updated);
    sessionStorage.setItem('cart', JSON.stringify(updated));
  };

  const total = cart.reduce((sum, p) => sum + p.price, 0);

  if (cart.length === 0) {
    return (
      <div style={styles.empty}>
        <h2 style={styles.title}>Your Cart is Empty</h2>
        <p style={styles.subtitle}>Add some products to get started.</p>
        <Link to="/products" style={styles.link}>Browse Products</Link>
      </div>
    );
  }

  return (
    <div>
      <h1 style={styles.title}>Shopping Cart</h1>
      <p style={styles.subtitle}>{cart.length} item{cart.length > 1 ? 's' : ''}</p>

      <div style={styles.items}>
        {cart.map((item, i) => (
          <div key={`${item.id}-${i}`} style={styles.item}>
            <span style={styles.itemIcon}>{item.image}</span>
            <div style={styles.itemInfo}>
              <span style={styles.itemName}>{item.name}</span>
              <span style={styles.itemPrice}>${item.price.toFixed(2)}</span>
            </div>
            <button
              style={styles.removeBtn}
              onClick={() => removeItem(i)}
              data-agenttel-target={`remove-${item.id}`}
            >
              Remove
            </button>
          </div>
        ))}
      </div>

      <div style={styles.summary}>
        <div style={styles.totalRow}>
          <span>Total</span>
          <span style={styles.totalAmount}>${total.toFixed(2)}</span>
        </div>
        <button
          style={styles.checkoutBtn}
          onClick={() => navigate('/checkout/shipping')}
          data-agenttel-target="proceed-to-checkout"
        >
          Proceed to Checkout
        </button>
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
    marginBottom: '24px',
  },
  empty: {
    textAlign: 'center' as const,
    padding: '64px 0',
  },
  link: {
    color: '#818cf8',
    fontSize: '16px',
  },
  items: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
    marginBottom: '32px',
  },
  item: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
    padding: '16px 20px',
    background: '#1e1b4b',
    borderRadius: '10px',
    border: '1px solid #312e81',
  },
  itemIcon: {
    fontSize: '32px',
  },
  itemInfo: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
  },
  itemName: {
    fontWeight: 600,
    color: '#e0e7ff',
  },
  itemPrice: {
    color: '#a5b4fc',
    fontWeight: 700,
  },
  removeBtn: {
    background: 'none',
    border: '1px solid #4338ca',
    color: '#94a3b8',
    padding: '8px 16px',
    borderRadius: '8px',
    cursor: 'pointer',
    fontSize: '13px',
  },
  summary: {
    background: '#1e1b4b',
    borderRadius: '12px',
    padding: '24px',
    border: '1px solid #312e81',
  },
  totalRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    fontSize: '18px',
    color: '#e0e7ff',
    marginBottom: '20px',
  },
  totalAmount: {
    fontSize: '24px',
    fontWeight: 700,
    color: '#a5b4fc',
  },
  checkoutBtn: {
    width: '100%',
    background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
    color: '#fff',
    border: 'none',
    padding: '16px',
    borderRadius: '10px',
    fontSize: '16px',
    fontWeight: 700,
    cursor: 'pointer',
  },
};
