package io.agenttel.agent.identity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maps agent roles to permissions and tools to required permission levels.
 * Controls which MCP tools each agent role can invoke.
 */
public class ToolPermissionRegistry {

    private final Map<String, Set<ToolPermission>> rolePermissions = new ConcurrentHashMap<>();
    private final Map<String, ToolPermission> toolRequirements = new ConcurrentHashMap<>();

    public ToolPermissionRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        // Default role profiles
        rolePermissions.put("observer", EnumSet.of(ToolPermission.READ));
        rolePermissions.put("diagnostician", EnumSet.of(ToolPermission.READ, ToolPermission.DIAGNOSE));
        rolePermissions.put("remediator", EnumSet.of(ToolPermission.READ, ToolPermission.DIAGNOSE, ToolPermission.REMEDIATE));
        rolePermissions.put("admin", EnumSet.allOf(ToolPermission.class));

        // Tool → required permission
        // READ tools
        for (String tool : List.of(
                "get_service_health", "get_incident_context", "get_error_analysis",
                "get_slo_report", "get_executive_summary", "get_trend_analysis",
                "get_playbook", "get_session")) {
            toolRequirements.put(tool, ToolPermission.READ);
        }

        // DIAGNOSE tools
        for (String tool : List.of(
                "verify_remediation_effect", "create_session", "add_session_entry")) {
            toolRequirements.put(tool, ToolPermission.DIAGNOSE);
        }

        // REMEDIATE tools
        for (String tool : List.of(
                "execute_remediation", "list_remediation_actions")) {
            toolRequirements.put(tool, ToolPermission.REMEDIATE);
        }
    }

    /**
     * Set permissions for a role, replacing any existing permissions.
     */
    public void setRolePermissions(String role, Set<ToolPermission> permissions) {
        rolePermissions.put(role.toLowerCase(), EnumSet.copyOf(permissions));
    }

    /**
     * Set the required permission level for a tool.
     */
    public void setToolRequirement(String toolName, ToolPermission permission) {
        toolRequirements.put(toolName, permission);
    }

    /**
     * Check if an agent is allowed to invoke a tool.
     * Unknown tools are allowed by default (READ level).
     * Anonymous agents get observer permissions.
     */
    public boolean isAllowed(AgentIdentity agent, String toolName) {
        String role = agent != null ? agent.role() : "observer";
        Set<ToolPermission> granted = rolePermissions.getOrDefault(
                role.toLowerCase(), rolePermissions.get("observer"));
        if (granted == null) {
            granted = EnumSet.of(ToolPermission.READ);
        }
        ToolPermission required = toolRequirements.getOrDefault(toolName, ToolPermission.READ);
        return granted.contains(required);
    }

    /**
     * Get the permissions granted to a role.
     */
    public Set<ToolPermission> getPermissions(String role) {
        return Collections.unmodifiableSet(
                rolePermissions.getOrDefault(role.toLowerCase(), EnumSet.of(ToolPermission.READ)));
    }

    /**
     * Get the required permission for a tool.
     */
    public ToolPermission getToolRequirement(String toolName) {
        return toolRequirements.getOrDefault(toolName, ToolPermission.READ);
    }
}
