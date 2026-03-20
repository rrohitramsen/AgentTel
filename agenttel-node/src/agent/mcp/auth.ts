/**
 * Authentication and role-based permissions for the MCP server.
 *
 * Opt-in: when no AuthConfig is provided, all requests are allowed.
 */

/** Permission levels for MCP tool access. */
export enum ToolPermission {
  READ = 'read',
  DIAGNOSE = 'diagnose',
  REMEDIATE = 'remediate',
  ADMIN = 'admin',
}

/** Identifies an authenticated agent making MCP requests. */
export interface AgentIdentity {
  agentId: string;
  role: string;
  sessionId?: string;
}

/**
 * Configuration for API key authentication.
 * Provide either a static apiKeys map, a custom validateKey function, or both.
 * When both are provided, the static map is checked first.
 */
export interface AuthConfig {
  /** Static map of bearer tokens to agent identities. */
  apiKeys?: Record<string, AgentIdentity>;
  /** Custom validator function. Return null to reject the key. */
  validateKey?: (key: string) => AgentIdentity | null;
}

/**
 * Resolves a bearer token to an agent identity using the given auth config.
 * Returns null if the key is invalid or not found.
 */
export function resolveKey(config: AuthConfig, key: string): AgentIdentity | null {
  const trimmed = key.trim();
  if (!trimmed) return null;

  if (config.apiKeys) {
    const identity = config.apiKeys[trimmed];
    if (identity) return identity;
  }

  if (config.validateKey) {
    return config.validateKey(trimmed);
  }

  return null;
}

/** Default role-to-permission mappings. */
const DEFAULT_ROLE_PERMISSIONS: Record<string, Set<ToolPermission>> = {
  observer: new Set([ToolPermission.READ]),
  diagnostician: new Set([ToolPermission.READ, ToolPermission.DIAGNOSE]),
  remediator: new Set([ToolPermission.READ, ToolPermission.DIAGNOSE, ToolPermission.REMEDIATE]),
  admin: new Set([ToolPermission.READ, ToolPermission.DIAGNOSE, ToolPermission.REMEDIATE, ToolPermission.ADMIN]),
};

/** Default tool-to-required-permission mappings. */
const DEFAULT_TOOL_PERMISSIONS: Record<string, ToolPermission> = {
  // READ tools
  get_service_health: ToolPermission.READ,
  get_operation_baselines: ToolPermission.READ,
  get_anomalies: ToolPermission.READ,
  get_slo_status: ToolPermission.READ,
  get_dependency_health: ToolPermission.READ,
  get_topology: ToolPermission.READ,
  get_incident_context: ToolPermission.READ,
  get_error_classification: ToolPermission.READ,
  get_deployment_info: ToolPermission.READ,
  get_slo_report: ToolPermission.READ,
  get_executive_summary: ToolPermission.READ,
  get_trend_analysis: ToolPermission.READ,
  get_playbook: ToolPermission.READ,
  get_session: ToolPermission.READ,
  // DIAGNOSE tools
  verify_remediation_effect: ToolPermission.DIAGNOSE,
  create_session: ToolPermission.DIAGNOSE,
  add_session_entry: ToolPermission.DIAGNOSE,
  suggest_remediation: ToolPermission.DIAGNOSE,
  get_change_correlation: ToolPermission.DIAGNOSE,
  // REMEDIATE tools
  execute_remediation: ToolPermission.REMEDIATE,
  list_remediation_actions: ToolPermission.REMEDIATE,
};

/**
 * Maps roles to granted permissions and tools to required permission levels.
 * Pre-populated with sensible defaults; fully customizable.
 */
export class ToolPermissionRegistry {
  private rolePermissions = new Map<string, Set<ToolPermission>>();
  private toolPermissions = new Map<string, ToolPermission>();

  constructor() {
    // Populate defaults
    for (const [role, perms] of Object.entries(DEFAULT_ROLE_PERMISSIONS)) {
      this.rolePermissions.set(role, new Set(perms));
    }
    for (const [tool, perm] of Object.entries(DEFAULT_TOOL_PERMISSIONS)) {
      this.toolPermissions.set(tool, perm);
    }
  }

  /** Set the permissions granted to a role, replacing any existing mapping. */
  setRolePermissions(role: string, perms: ToolPermission[]): void {
    this.rolePermissions.set(role.toLowerCase(), new Set(perms));
  }

  /** Set the required permission level for a specific tool. */
  setToolPermission(toolName: string, perm: ToolPermission): void {
    this.toolPermissions.set(toolName, perm);
  }

  /**
   * Check whether the given identity has permission to invoke the named tool.
   * Unknown tools default to READ. Unknown roles get observer permissions.
   */
  isAllowed(identity: AgentIdentity, toolName: string): boolean {
    const role = identity.role.toLowerCase();
    const granted = this.rolePermissions.get(role) ?? this.rolePermissions.get('observer') ?? new Set<ToolPermission>();
    const required = this.toolPermissions.get(toolName) ?? ToolPermission.READ;
    return granted.has(required);
  }
}
