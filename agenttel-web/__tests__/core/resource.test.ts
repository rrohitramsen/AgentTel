import { buildResourceAttributes } from '../../src/core/resource';
import { AgentTelClientAttributes } from '../../src/core/attribute-keys';
import type { AgentTelWebConfig } from '../../src/config/types';

describe('buildResourceAttributes', () => {
  it('includes required attributes', () => {
    const config: AgentTelWebConfig = {
      appName: 'my-app',
      collectorEndpoint: '/otlp',
    };
    const attrs = buildResourceAttributes(config);

    expect(attrs['service.name']).toBe('my-app');
    expect(attrs[AgentTelClientAttributes.APP_NAME]).toBe('my-app');
    expect(attrs[AgentTelClientAttributes.APP_PLATFORM]).toBe('web');
    expect(attrs[AgentTelClientAttributes.DECISION_USER_FACING]).toBe(true);
  });

  it('includes optional attributes when provided', () => {
    const config: AgentTelWebConfig = {
      appName: 'checkout-web',
      appVersion: '2.3.1',
      environment: 'production',
      team: 'payments',
      domain: 'commerce',
      collectorEndpoint: '/otlp',
    };
    const attrs = buildResourceAttributes(config);

    expect(attrs['service.version']).toBe('2.3.1');
    expect(attrs[AgentTelClientAttributes.APP_VERSION]).toBe('2.3.1');
    expect(attrs['deployment.environment']).toBe('production');
    expect(attrs[AgentTelClientAttributes.APP_ENVIRONMENT]).toBe('production');
    expect(attrs[AgentTelClientAttributes.TOPOLOGY_TEAM]).toBe('payments');
    expect(attrs[AgentTelClientAttributes.TOPOLOGY_DOMAIN]).toBe('commerce');
  });

  it('omits optional attributes when not provided', () => {
    const config: AgentTelWebConfig = {
      appName: 'minimal',
      collectorEndpoint: '/otlp',
    };
    const attrs = buildResourceAttributes(config);

    expect(attrs['service.version']).toBeUndefined();
    expect(attrs[AgentTelClientAttributes.TOPOLOGY_TEAM]).toBeUndefined();
    expect(attrs[AgentTelClientAttributes.TOPOLOGY_DOMAIN]).toBeUndefined();
  });

  it('uses custom platform when specified', () => {
    const config: AgentTelWebConfig = {
      appName: 'mobile-app',
      platform: 'mobile',
      collectorEndpoint: '/otlp',
    };
    const attrs = buildResourceAttributes(config);

    expect(attrs[AgentTelClientAttributes.APP_PLATFORM]).toBe('mobile');
  });
});
