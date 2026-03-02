import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ProductCard, Product } from '../components/ProductCard';

const PRODUCTS: Product[] = [
  {
    id: 'agent-core',
    name: 'AgentTel Core',
    price: 49.99,
    image: '🔭',
    description: 'Lightweight telemetry SDK with zero-config auto-instrumentation for Spring Boot services.',
  },
  {
    id: 'agent-monitor',
    name: 'AgentTel Monitor',
    price: 79.99,
    image: '🤖',
    description: 'AI-powered monitoring agent that watches, reasons, and acts on service health autonomously.',
  },
  {
    id: 'agent-web',
    name: 'AgentTel Web SDK',
    price: 29.99,
    image: '🌐',
    description: 'Browser-side telemetry with cross-stack correlation, anomaly detection, and journey tracking.',
  },
  {
    id: 'agent-reporting',
    name: 'AgentTel Reporting',
    price: 39.99,
    image: '📊',
    description: 'SLO dashboards, trend analysis, and executive summaries accessible via MCP tools.',
  },
];

export const ProductsPage: React.FC = () => {
  const navigate = useNavigate();
  const [cart, setCart] = useState<Product[]>(() => {
    const saved = sessionStorage.getItem('cart');
    return saved ? JSON.parse(saved) : [];
  });

  const addToCart = (product: Product) => {
    const updated = [...cart, product];
    setCart(updated);
    sessionStorage.setItem('cart', JSON.stringify(updated));
  };

  return (
    <div>
      <div style={styles.header}>
        <div>
          <h1 style={styles.title}>Products</h1>
          <p style={styles.subtitle}>Browse the AgentTel ecosystem</p>
        </div>
        {cart.length > 0 && (
          <button style={styles.cartButton} onClick={() => navigate('/cart')}>
            Cart ({cart.length}) — ${cart.reduce((sum, p) => sum + p.price, 0).toFixed(2)}
          </button>
        )}
      </div>

      <div style={styles.grid}>
        {PRODUCTS.map((product) => (
          <ProductCard key={product.id} product={product} onAddToCart={addToCart} />
        ))}
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: '32px',
  },
  title: {
    fontSize: '28px',
    fontWeight: 700,
    color: '#e0e7ff',
    marginBottom: '4px',
  },
  subtitle: {
    fontSize: '16px',
    color: '#94a3b8',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
    gap: '24px',
  },
  cartButton: {
    background: '#6366f1',
    color: '#fff',
    border: 'none',
    padding: '12px 24px',
    borderRadius: '10px',
    fontSize: '14px',
    fontWeight: 600,
    cursor: 'pointer',
    whiteSpace: 'nowrap' as const,
  },
};
