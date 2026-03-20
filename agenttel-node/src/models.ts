export type {
  OperationBaseline,
  DependencyDescriptor,
  ConsumerDescriptor,
  SLODefinition,
  AnomalyResult,
  SLOStatus,
  SLOAlert,
  ErrorClassification,
  RollingSnapshot,
} from './interfaces.js';

export {
  normalResult,
  isWithinBudget,
  isSnapshotEmpty,
  snapshotConfidence,
} from './interfaces.js';
