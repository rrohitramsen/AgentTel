import type { AgentTelWebConfig } from '../../../src/config/types';

export const agenttelConfig: AgentTelWebConfig = {
  appName: 'checkout-web',
  appVersion: '1.0.0',
  environment: 'development',
  collectorEndpoint: '/otlp',
  team: 'frontend',
  domain: 'commerce',

  routes: {
    '/products': {
      businessCriticality: 'engagement',
      baseline: { pageLoadP50Ms: 800, pageLoadP99Ms: 2500 },
    },
    '/cart': {
      businessCriticality: 'revenue',
      baseline: { pageLoadP50Ms: 600, pageLoadP99Ms: 2000 },
    },
    '/checkout/shipping': {
      businessCriticality: 'revenue',
      baseline: { pageLoadP50Ms: 500, pageLoadP99Ms: 1500 },
    },
    '/checkout/payment': {
      businessCriticality: 'revenue',
      baseline: { pageLoadP50Ms: 700, pageLoadP99Ms: 2000, apiCallP50Ms: 300 },
    },
    '/order/confirmation': {
      businessCriticality: 'revenue',
      baseline: { pageLoadP50Ms: 400, pageLoadP99Ms: 1200 },
    },
  },

  journeys: {
    checkout: {
      steps: ['/products', '/cart', '/checkout/shipping', '/checkout/payment', '/order/confirmation'],
      baseline: {
        completionRate: 0.65,
        avgDurationS: 300, // 5 minutes
      },
    },
  },

  anomalyDetection: {
    rageClickThreshold: 3,
    rageClickWindowMs: 2000,
    errorLoopThreshold: 5,
    errorLoopWindowMs: 30_000,
    apiFailureCascadeThreshold: 3,
    apiFailureCascadeWindowMs: 10_000,
  },

  samplingRate: 1.0,
  debug: true,
};
