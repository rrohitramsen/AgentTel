package io.agenttel.agent.mcp;

import java.util.Map;

/**
 * Handler for an MCP tool invocation.
 */
@FunctionalInterface
public interface McpToolHandler {

    /**
     * Executes the tool with the given arguments and returns the result text.
     */
    String handle(Map<String, String> arguments);
}
