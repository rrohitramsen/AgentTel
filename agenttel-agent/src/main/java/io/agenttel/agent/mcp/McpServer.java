package io.agenttel.agent.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Lightweight MCP (Model Context Protocol) server using JDK's built-in HttpServer.
 * Exposes telemetry tools for AI agents over HTTP with JSON-RPC.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /mcp — JSON-RPC 2.0 endpoint for tool listing and invocation</li>
 *   <li>GET /mcp/docs — HTML documentation page for all registered tools</li>
 *   <li>GET /health — Server health check</li>
 * </ul>
 *
 * <p>Supported JSON-RPC methods:
 * <ul>
 *   <li>tools/list — List available tools</li>
 *   <li>tools/call — Invoke a tool</li>
 *   <li>initialize — Handshake</li>
 * </ul>
 */
public class McpServer {

    private static final Logger LOG = LoggerFactory.getLogger(McpServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int port;
    private final Map<String, McpToolDefinition> toolDefinitions = new ConcurrentHashMap<>();
    private final Map<String, McpToolHandler> toolHandlers = new ConcurrentHashMap<>();
    private HttpServer httpServer;

    public McpServer(int port) {
        this.port = port;
    }

    /**
     * Registers a tool with its definition and handler.
     */
    public void registerTool(McpToolDefinition definition, McpToolHandler handler) {
        toolDefinitions.put(definition.name(), definition);
        toolHandlers.put(definition.name(), handler);
    }

    /**
     * Starts the MCP server.
     */
    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(4));

        httpServer.createContext("/mcp", this::handleMcpRequest);
        httpServer.createContext("/mcp/docs", this::handleDocs);
        httpServer.createContext("/health", this::handleHealthCheck);

        httpServer.start();
        LOG.info("MCP server started on port {}", port);
    }

    /**
     * Stops the MCP server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            LOG.info("MCP server stopped");
        }
    }

    /**
     * Returns the port this server is bound to.
     */
    public int getPort() {
        return port;
    }

    private void handleDocs(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String html = McpDocsHandler.generateHtml(toolDefinitions.values());
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleHealthCheck(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"ok\",\"tools\":" + toolDefinitions.size() + "}";
        sendResponse(exchange, 200, response);
    }

    private void handleMcpRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body;
        try (var is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        try {
            JsonNode request = MAPPER.readTree(body);
            String method = request.has("method") ? request.get("method").asText() : "";
            JsonNode id = request.get("id");
            JsonNode params = request.get("params");

            Object result;
            switch (method) {
                case "initialize" -> result = handleInitialize();
                case "tools/list" -> result = handleToolsList();
                case "tools/call" -> result = handleToolsCall(params);
                default -> {
                    sendJsonRpcError(exchange, id, -32601, "Method not found: " + method);
                    return;
                }
            }

            sendJsonRpcResult(exchange, id, result);
        } catch (JsonProcessingException e) {
            sendJsonRpcError(exchange, null, -32700, "Parse error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error handling MCP request", e);
            sendJsonRpcError(exchange, null, -32603, "Internal error: " + e.getMessage());
        }
    }

    private Map<String, Object> handleInitialize() {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of(
                        "name", "agenttel-mcp",
                        "version", "0.1.0"
                )
        );
    }

    private Map<String, Object> handleToolsList() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpToolDefinition def : toolDefinitions.values()) {
            tools.add(def.toJsonMap());
        }
        return Map.of("tools", tools);
    }

    private Map<String, Object> handleToolsCall(JsonNode params) {
        if (params == null || !params.has("name")) {
            throw new IllegalArgumentException("Missing tool name");
        }

        String toolName = params.get("name").asText();
        McpToolHandler handler = toolHandlers.get(toolName);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        Map<String, String> arguments = new LinkedHashMap<>();
        if (params.has("arguments")) {
            JsonNode args = params.get("arguments");
            args.fieldNames().forEachRemaining(field ->
                    arguments.put(field, args.get(field).asText()));
        }

        String resultText = handler.handle(arguments);

        return Map.of(
                "content", List.of(Map.of(
                        "type", "text",
                        "text", resultText
                )),
                "isError", false
        );
    }

    private void sendJsonRpcResult(HttpExchange exchange, JsonNode id, Object result) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id != null ? id.asInt() : 1);
        response.put("result", result);
        sendResponse(exchange, 200, MAPPER.writeValueAsString(response));
    }

    private void sendJsonRpcError(HttpExchange exchange, JsonNode id, int code, String message) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id != null ? id.asInt() : null);
        response.put("error", Map.of("code", code, "message", message));
        sendResponse(exchange, 200, MAPPER.writeValueAsString(response));
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
