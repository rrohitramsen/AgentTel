package io.agenttel.agent.identity;

import java.util.Map;

/**
 * Represents the identity of an AI agent making MCP requests.
 * Extracted from HTTP headers or tool argument meta-parameters.
 */
public record AgentIdentity(String agentId, String role, String sessionId) {

    public static final String HEADER_AGENT_ID = "X-Agent-Id";
    public static final String HEADER_AGENT_ROLE = "X-Agent-Role";
    public static final String HEADER_AGENT_SESSION_ID = "X-Agent-Session-Id";

    public static final String ARG_AGENT_ID = "_agent_id";
    public static final String ARG_AGENT_ROLE = "_agent_role";
    public static final String ARG_AGENT_SESSION_ID = "_agent_session_id";

    public static final AgentIdentity ANONYMOUS =
            new AgentIdentity("anonymous", "observer", null);

    /**
     * Extract agent identity from HTTP headers.
     */
    public static AgentIdentity fromHeaders(Map<String, String> headers) {
        if (headers == null) {
            return ANONYMOUS;
        }
        String id = headers.get(HEADER_AGENT_ID);
        if (id == null || id.isBlank()) {
            return ANONYMOUS;
        }
        String role = headers.getOrDefault(HEADER_AGENT_ROLE, "observer");
        String sessionId = headers.get(HEADER_AGENT_SESSION_ID);
        return new AgentIdentity(id, role, sessionId);
    }

    /**
     * Extract agent identity from tool arguments (meta-parameters prefixed with _agent_).
     */
    public static AgentIdentity fromArguments(Map<String, String> args) {
        if (args == null) {
            return ANONYMOUS;
        }
        String id = args.get(ARG_AGENT_ID);
        if (id == null || id.isBlank()) {
            return ANONYMOUS;
        }
        String role = args.getOrDefault(ARG_AGENT_ROLE, "observer");
        String sessionId = args.get(ARG_AGENT_SESSION_ID);
        return new AgentIdentity(id, role, sessionId);
    }

    /**
     * Merge identity from headers and arguments. Arguments take precedence.
     */
    public static AgentIdentity resolve(Map<String, String> headers, Map<String, String> args) {
        AgentIdentity fromArgs = fromArguments(args);
        if (!fromArgs.isAnonymous()) {
            return fromArgs;
        }
        return fromHeaders(headers);
    }

    public boolean isAnonymous() {
        return "anonymous".equals(agentId);
    }
}
