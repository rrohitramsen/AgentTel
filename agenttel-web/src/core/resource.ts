import { AgentTelClientAttributes } from './attribute-keys';
import type { AttributeValue } from '../types/span';
import type { AgentTelWebConfig } from '../config/types';

/**
 * Builds OTel Resource attributes from SDK configuration.
 * Set once per app — identifies the client application.
 */
export function buildResourceAttributes(config: AgentTelWebConfig): Record<string, AttributeValue> {
  const attrs: Record<string, AttributeValue> = {
    'service.name': config.appName,
    [AgentTelClientAttributes.APP_NAME]: config.appName,
    [AgentTelClientAttributes.APP_PLATFORM]: config.platform ?? 'web',
    [AgentTelClientAttributes.DECISION_USER_FACING]: true,
  };

  if (config.appVersion) {
    attrs['service.version'] = config.appVersion;
    attrs[AgentTelClientAttributes.APP_VERSION] = config.appVersion;
  }
  if (config.environment) {
    attrs['deployment.environment'] = config.environment;
    attrs[AgentTelClientAttributes.APP_ENVIRONMENT] = config.environment;
  }
  if (config.team) {
    attrs[AgentTelClientAttributes.TOPOLOGY_TEAM] = config.team;
  }
  if (config.domain) {
    attrs[AgentTelClientAttributes.TOPOLOGY_DOMAIN] = config.domain;
  }

  return attrs;
}
