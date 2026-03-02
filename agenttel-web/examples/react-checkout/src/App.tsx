import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProductsPage } from './pages/ProductsPage';
import { CartPage } from './pages/CartPage';
import { CheckoutShippingPage } from './pages/CheckoutShippingPage';
import { CheckoutPaymentPage } from './pages/CheckoutPaymentPage';
import { OrderConfirmationPage } from './pages/OrderConfirmationPage';

export const App: React.FC = () => {
  return (
    <Layout>
      <Routes>
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route path="/checkout/shipping" element={<CheckoutShippingPage />} />
        <Route path="/checkout/payment" element={<CheckoutPaymentPage />} />
        <Route path="/order/confirmation" element={<OrderConfirmationPage />} />
        <Route path="*" element={<Navigate to="/products" replace />} />
      </Routes>
    </Layout>
  );
};
