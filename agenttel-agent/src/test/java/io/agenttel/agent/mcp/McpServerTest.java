package io.agenttel.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class McpServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private McpServer server;
    private int port;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        port = 18081 + (int) (Math.random() * 1000);
        server = new McpServer(port);
        server.registerTool(
                new McpToolDefinition(
                        "test_tool",
                        "A test tool",
                        Map.of("input", new McpToolDefinition.ParameterDefinition("string", "Test input")),
                        List.of("input")
                ),
                args -> "Result: " + args.get("input")
        );
        server.start();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void healthCheck_returnsOk() throws Exception {
        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/health"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("status").asText()).isEqualTo("ok");
        assertThat(body.get("tools").asInt()).isEqualTo(1);
    }

    @Test
    void initialize_returnsProtocolVersion() throws Exception {
        String request = MAPPER.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of()));

        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/mcp"))
                        .POST(HttpRequest.BodyPublishers.ofString(request))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("result").get("protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(body.get("result").get("serverInfo").get("name").asText()).isEqualTo("agenttel-mcp");
    }

    @Test
    void toolsList_returnsRegisteredTools() throws Exception {
        String request = MAPPER.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "tools/list", "params", Map.of()));

        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/mcp"))
                        .POST(HttpRequest.BodyPublishers.ofString(request))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode body = MAPPER.readTree(response.body());
        JsonNode tools = body.get("result").get("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(1);
        assertThat(tools.get(0).get("name").asText()).isEqualTo("test_tool");
    }

    @Test
    void toolsCall_executesTool() throws Exception {
        String request = MAPPER.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "tools/call",
                "params", Map.of("name", "test_tool", "arguments", Map.of("input", "hello"))));

        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/mcp"))
                        .POST(HttpRequest.BodyPublishers.ofString(request))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode body = MAPPER.readTree(response.body());
        JsonNode content = body.get("result").get("content");
        assertThat(content.get(0).get("text").asText()).isEqualTo("Result: hello");
    }

    @Test
    void toolsCall_unknownTool_returnsError() throws Exception {
        String request = MAPPER.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "tools/call",
                "params", Map.of("name", "nonexistent")));

        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/mcp"))
                        .POST(HttpRequest.BodyPublishers.ofString(request))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.has("error")).isTrue();
        assertThat(body.get("error").get("message").asText()).contains("Unknown tool");
    }

    @Test
    void unknownMethod_returnsMethodNotFoundError() throws Exception {
        String request = MAPPER.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "nonexistent"));

        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/mcp"))
                        .POST(HttpRequest.BodyPublishers.ofString(request))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.has("error")).isTrue();
        assertThat(body.get("error").get("code").asInt()).isEqualTo(-32601);
    }
}
