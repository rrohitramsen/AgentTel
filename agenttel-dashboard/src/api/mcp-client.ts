/**
 * MCP JSON-RPC 2.0 client for querying AgentTel tools.
 */
export class McpClient {
  private baseUrl: string;
  private requestId = 0;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  async callTool(toolName: string, args: Record<string, string> = {}): Promise<string> {
    const response = await fetch(this.baseUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        jsonrpc: '2.0',
        method: 'tools/call',
        params: {
          name: toolName,
          arguments: args,
        },
        id: ++this.requestId,
      }),
    });

    if (!response.ok) {
      throw new Error(`MCP request failed: ${response.status}`);
    }

    const rpc = await response.json();

    if (rpc.error) {
      throw new Error(rpc.error.message || 'MCP tool error');
    }

    const result = rpc.result;
    if (result?.content) {
      return result.content.map((c: { text?: string }) => c.text || '').join('\n');
    }

    return typeof result === 'string' ? result : JSON.stringify(result);
  }

  async listTools(): Promise<{ name: string; description: string }[]> {
    const response = await fetch(this.baseUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        jsonrpc: '2.0',
        method: 'tools/list',
        id: ++this.requestId,
      }),
    });

    const rpc = await response.json();
    return rpc.result?.tools || [];
  }
}
