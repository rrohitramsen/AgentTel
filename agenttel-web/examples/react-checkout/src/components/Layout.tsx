import React from 'react';
import { Link, useLocation } from 'react-router-dom';

const steps = [
  { path: '/products', label: 'Products' },
  { path: '/cart', label: 'Cart' },
  { path: '/checkout/shipping', label: 'Shipping' },
  { path: '/checkout/payment', label: 'Payment' },
  { path: '/order/confirmation', label: 'Confirmation' },
];

export const Layout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation();
  const currentIndex = steps.findIndex((s) => location.pathname.startsWith(s.path));

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <Link to="/products" style={styles.logo}>
          <span style={styles.logoIcon}>⚡</span> AgentTel Checkout Demo
        </Link>
        <nav style={styles.nav}>
          {steps.map((step, i) => (
            <Link
              key={step.path}
              to={step.path}
              style={{
                ...styles.navLink,
                ...(i === currentIndex ? styles.navLinkActive : {}),
                ...(i < currentIndex ? styles.navLinkCompleted : {}),
              }}
            >
              <span style={styles.stepNumber}>{i + 1}</span>
              {step.label}
            </Link>
          ))}
        </nav>
      </header>

      <main style={styles.main}>{children}</main>

      <footer style={styles.footer}>
        <p>
          Instrumented with <strong>@agenttel/web</strong> — traces visible in{' '}
          <a href="http://localhost:16686" target="_blank" rel="noreferrer" style={styles.link}>
            Jaeger
          </a>
        </p>
      </footer>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    minHeight: '100vh',
    display: 'flex',
    flexDirection: 'column',
  },
  header: {
    background: 'linear-gradient(135deg, #1e1b4b 0%, #312e81 100%)',
    padding: '16px 32px',
    borderBottom: '1px solid #4338ca',
  },
  logo: {
    color: '#e0e7ff',
    textDecoration: 'none',
    fontSize: '20px',
    fontWeight: 700,
    display: 'block',
    marginBottom: '12px',
  },
  logoIcon: {
    fontSize: '24px',
  },
  nav: {
    display: 'flex',
    gap: '8px',
    flexWrap: 'wrap' as const,
  },
  navLink: {
    color: '#94a3b8',
    textDecoration: 'none',
    padding: '8px 16px',
    borderRadius: '8px',
    fontSize: '14px',
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    transition: 'all 0.2s',
  },
  navLinkActive: {
    color: '#e0e7ff',
    background: 'rgba(99, 102, 241, 0.3)',
    border: '1px solid #6366f1',
  },
  navLinkCompleted: {
    color: '#a5b4fc',
  },
  stepNumber: {
    width: '24px',
    height: '24px',
    borderRadius: '50%',
    background: 'rgba(99, 102, 241, 0.2)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '12px',
    fontWeight: 600,
  },
  main: {
    flex: 1,
    padding: '32px',
    maxWidth: '960px',
    margin: '0 auto',
    width: '100%',
  },
  footer: {
    textAlign: 'center' as const,
    padding: '16px',
    borderTop: '1px solid #1e1b4b',
    fontSize: '13px',
    color: '#64748b',
  },
  link: {
    color: '#818cf8',
  },
};
