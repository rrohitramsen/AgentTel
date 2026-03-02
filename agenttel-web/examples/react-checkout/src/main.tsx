import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { agenttelConfig } from './agenttel.config';

// Initialize AgentTel Web SDK
// In a real app this would import from '@agenttel/web'
// For the demo, we initialize it inline
const initAgentTel = async () => {
  try {
    // Dynamic import to avoid blocking render
    const { AgentTelWeb } = await import('../../../src/index');
    AgentTelWeb.init(agenttelConfig);
    console.log('[AgentTel] Web SDK initialized');
  } catch (e) {
    console.warn('[AgentTel] Failed to initialize:', e);
  }
};

initAgentTel();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
