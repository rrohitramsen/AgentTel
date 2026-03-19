import type { DependencyDescriptor, ConsumerDescriptor } from '../interfaces.js';

/** Service topology metadata registry. */
export class TopologyRegistry {
  private _team = '';
  private _tier = '';
  private _domain = '';
  private _onCallChannel = '';
  private _repoUrl = '';
  private readonly deps = new Map<string, DependencyDescriptor>();
  private readonly _consumers: ConsumerDescriptor[] = [];

  get team(): string { return this._team; }
  set team(v: string) { this._team = v; }

  get tier(): string { return this._tier; }
  set tier(v: string) { this._tier = v; }

  get domain(): string { return this._domain; }
  set domain(v: string) { this._domain = v; }

  get onCallChannel(): string { return this._onCallChannel; }
  set onCallChannel(v: string) { this._onCallChannel = v; }

  get repoUrl(): string { return this._repoUrl; }
  set repoUrl(v: string) { this._repoUrl = v; }

  registerDependency(dep: DependencyDescriptor): void {
    this.deps.set(dep.name, dep);
  }

  registerConsumer(consumer: ConsumerDescriptor): void {
    this._consumers.push(consumer);
  }

  getDependency(name: string): DependencyDescriptor | undefined {
    return this.deps.get(name);
  }

  dependencies(): DependencyDescriptor[] {
    return [...this.deps.values()];
  }

  consumers(): ConsumerDescriptor[] {
    return [...this._consumers];
  }

  serializeDependenciesJSON(): string {
    return JSON.stringify(this.dependencies());
  }

  serializeConsumersJSON(): string {
    return JSON.stringify(this.consumers());
  }
}
