export type HealthStatus = 'healthy' | 'degraded' | 'unhealthy' | 'unknown';

export interface ComponentHealth {
  name: string;
  status: HealthStatus;
  message?: string;
  updatedAt: Date;
}

export interface ServiceHealth {
  overall: HealthStatus;
  components: Record<string, ComponentHealth>;
  updatedAt: Date;
}

/** Aggregates component health into overall service health. */
export class HealthAggregator {
  private readonly components = new Map<string, ComponentHealth>();

  report(name: string, status: HealthStatus, message = ''): void {
    this.components.set(name, { name, status, message, updatedAt: new Date() });
  }

  getHealth(): ServiceHealth {
    const components: Record<string, ComponentHealth> = {};
    let overall: HealthStatus = 'healthy';

    for (const [key, comp] of this.components) {
      components[key] = comp;
      if (comp.status === 'unhealthy') overall = 'unhealthy';
      else if (comp.status === 'degraded' && overall !== 'unhealthy') overall = 'degraded';
    }

    return { overall, components, updatedAt: new Date() };
  }
}
