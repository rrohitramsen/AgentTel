package io.agenttel.agent.mcp;

import java.util.List;
import java.util.Map;

/**
 * Defines an MCP tool that AI agents can invoke.
 * Follows the Model Context Protocol tool specification.
 */
public record McpToolDefinition(
        String name,
        String description,
        Map<String, ParameterDefinition> parameters,
        List<String> required
) {

    public record ParameterDefinition(
            String type,
            String description
    ) {}

    /**
     * Serializes this tool definition to a JSON-compatible map.
     */
    public Map<String, Object> toJsonMap() {
        Map<String, Object> inputSchema = new java.util.LinkedHashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> props = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, ParameterDefinition> entry : parameters.entrySet()) {
            props.put(entry.getKey(), Map.of(
                    "type", entry.getValue().type(),
                    "description", entry.getValue().description()
            ));
        }
        inputSchema.put("properties", props);
        inputSchema.put("required", required);

        return Map.of(
                "name", name,
                "description", description,
                "inputSchema", inputSchema
        );
    }
}
