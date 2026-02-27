package io.agenttel.agent.mcp;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates an HTML documentation page for all registered MCP tools.
 * Served at {@code GET /mcp/docs} by the MCP server.
 */
final class McpDocsHandler {

    private McpDocsHandler() {}

    static String generateHtml(Collection<McpToolDefinition> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>AgentTel MCP Tools</title>
                <style>
                  * { box-sizing: border-box; margin: 0; padding: 0; }
                  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                         line-height: 1.6; color: #1a1a2e; background: #f8f9fa; padding: 2rem; max-width: 960px; margin: 0 auto; }
                  h1 { font-size: 1.8rem; margin-bottom: 0.5rem; }
                  .subtitle { color: #666; margin-bottom: 2rem; }
                  .tool { background: #fff; border: 1px solid #dee2e6; border-radius: 8px; padding: 1.5rem; margin-bottom: 1.5rem; }
                  .tool-name { font-size: 1.2rem; font-family: 'SF Mono', Monaco, monospace; color: #0d6efd; }
                  .tool-desc { color: #444; margin: 0.5rem 0 1rem; }
                  h3 { font-size: 0.9rem; text-transform: uppercase; letter-spacing: 0.05em; color: #888; margin-bottom: 0.5rem; }
                  table { width: 100%; border-collapse: collapse; margin-bottom: 1rem; }
                  th, td { text-align: left; padding: 0.5rem 0.75rem; border-bottom: 1px solid #eee; }
                  th { font-size: 0.85rem; color: #666; }
                  td code { font-family: 'SF Mono', Monaco, monospace; font-size: 0.9rem; }
                  .required { color: #dc3545; font-size: 0.8rem; font-weight: 600; }
                  .optional { color: #6c757d; font-size: 0.8rem; }
                  pre { background: #1e1e2e; color: #cdd6f4; padding: 1rem; border-radius: 6px;
                        overflow-x: auto; font-size: 0.85rem; line-height: 1.5; }
                  .no-params { color: #888; font-style: italic; }
                  .toc { margin-bottom: 2rem; }
                  .toc a { color: #0d6efd; text-decoration: none; }
                  .toc a:hover { text-decoration: underline; }
                  .toc li { margin-bottom: 0.3rem; }
                </style>
                </head>
                <body>
                <h1>AgentTel MCP Tools</h1>
                <p class="subtitle">Model Context Protocol tools exposed by the AgentTel MCP server. \
                Use JSON-RPC 2.0 over <code>POST /mcp</code> to invoke these tools.</p>
                """);

        // Table of contents
        sb.append("<div class=\"toc\"><h3>Tools</h3><ul>\n");
        for (McpToolDefinition tool : tools) {
            sb.append("<li><a href=\"#").append(escapeHtml(tool.name())).append("\">")
                    .append(escapeHtml(tool.name())).append("</a> &mdash; ")
                    .append(escapeHtml(tool.description())).append("</li>\n");
        }
        sb.append("</ul></div>\n");

        // Tool details
        for (McpToolDefinition tool : tools) {
            sb.append("<div class=\"tool\" id=\"").append(escapeHtml(tool.name())).append("\">\n");
            sb.append("<div class=\"tool-name\">").append(escapeHtml(tool.name())).append("</div>\n");
            sb.append("<p class=\"tool-desc\">").append(escapeHtml(tool.description())).append("</p>\n");

            // Parameters table
            sb.append("<h3>Parameters</h3>\n");
            if (tool.parameters().isEmpty()) {
                sb.append("<p class=\"no-params\">No parameters</p>\n");
            } else {
                sb.append("<table><thead><tr><th>Name</th><th>Type</th><th>Required</th><th>Description</th></tr></thead><tbody>\n");
                for (Map.Entry<String, McpToolDefinition.ParameterDefinition> entry : tool.parameters().entrySet()) {
                    String name = entry.getKey();
                    McpToolDefinition.ParameterDefinition param = entry.getValue();
                    boolean required = tool.required().contains(name);
                    sb.append("<tr>")
                            .append("<td><code>").append(escapeHtml(name)).append("</code></td>")
                            .append("<td><code>").append(escapeHtml(param.type())).append("</code></td>")
                            .append("<td>").append(required ? "<span class=\"required\">required</span>"
                                    : "<span class=\"optional\">optional</span>").append("</td>")
                            .append("<td>").append(escapeHtml(param.description())).append("</td>")
                            .append("</tr>\n");
                }
                sb.append("</tbody></table>\n");
            }

            // Example JSON-RPC request
            sb.append("<h3>Example Request</h3>\n");
            sb.append("<pre>").append(escapeHtml(buildExampleRequest(tool))).append("</pre>\n");

            // Curl command
            sb.append("<h3>curl</h3>\n");
            sb.append("<pre>").append(escapeHtml(buildCurlCommand(tool))).append("</pre>\n");

            sb.append("</div>\n");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String buildExampleRequest(McpToolDefinition tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"jsonrpc\": \"2.0\",\n");
        sb.append("  \"id\": 1,\n");
        sb.append("  \"method\": \"tools/call\",\n");
        sb.append("  \"params\": {\n");
        sb.append("    \"name\": \"").append(tool.name()).append("\",\n");
        sb.append("    \"arguments\": {");

        if (!tool.parameters().isEmpty()) {
            sb.append("\n");
            List<String> entries = tool.parameters().entrySet().stream()
                    .map(e -> "      \"" + e.getKey() + "\": \"<" + e.getKey() + ">\"")
                    .collect(Collectors.toList());
            sb.append(String.join(",\n", entries)).append("\n    ");
        }

        sb.append("}\n");
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    private static String buildCurlCommand(McpToolDefinition tool) {
        StringBuilder args = new StringBuilder("{");
        if (!tool.parameters().isEmpty()) {
            List<String> entries = tool.parameters().entrySet().stream()
                    .map(e -> "\\\"" + e.getKey() + "\\\": \\\"<" + e.getKey() + ">\\\"")
                    .collect(Collectors.toList());
            args.append(String.join(", ", entries));
        }
        args.append("}");

        return "curl -X POST http://localhost:8081/mcp \\\n"
                + "  -H \"Content-Type: application/json\" \\\n"
                + "  -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"" + tool.name() + "\",\"arguments\":" + args + "}}'";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
