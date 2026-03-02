import React from 'react';

export interface Product {
  id: string;
  name: string;
  price: number;
  image: string;
  description: string;
}

interface ProductCardProps {
  product: Product;
  onAddToCart: (product: Product) => void;
}

export const ProductCard: React.FC<ProductCardProps> = ({ product, onAddToCart }) => {
  return (
    <div style={styles.card}>
      <div style={styles.imageContainer}>
        <div style={styles.imagePlaceholder}>{product.image}</div>
      </div>
      <div style={styles.content}>
        <h3 style={styles.name}>{product.name}</h3>
        <p style={styles.description}>{product.description}</p>
        <div style={styles.footer}>
          <span style={styles.price}>${product.price.toFixed(2)}</span>
          <button
            style={styles.button}
            onClick={() => onAddToCart(product)}
            data-agenttel-target={`add-to-cart-${product.id}`}
          >
            Add to Cart
          </button>
        </div>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  card: {
    background: 'linear-gradient(135deg, #1e1b4b 0%, #1a1744 100%)',
    borderRadius: '12px',
    border: '1px solid #312e81',
    overflow: 'hidden',
    transition: 'transform 0.2s, border-color 0.2s',
  },
  imageContainer: {
    padding: '24px',
    display: 'flex',
    justifyContent: 'center',
    background: 'rgba(99, 102, 241, 0.05)',
  },
  imagePlaceholder: {
    fontSize: '64px',
    lineHeight: 1,
  },
  content: {
    padding: '16px 20px 20px',
  },
  name: {
    fontSize: '18px',
    fontWeight: 600,
    color: '#e0e7ff',
    marginBottom: '8px',
  },
  description: {
    fontSize: '14px',
    color: '#94a3b8',
    marginBottom: '16px',
    lineHeight: 1.5,
  },
  footer: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  price: {
    fontSize: '20px',
    fontWeight: 700,
    color: '#a5b4fc',
  },
  button: {
    background: '#6366f1',
    color: '#fff',
    border: 'none',
    padding: '10px 20px',
    borderRadius: '8px',
    fontSize: '14px',
    fontWeight: 600,
    cursor: 'pointer',
  },
};
