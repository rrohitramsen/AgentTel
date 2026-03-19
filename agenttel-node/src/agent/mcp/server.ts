export interface MCPTool {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
}

export type MCPToolHandler = (params: Record<string, unknown>) => Promise<unknown>;

interface RPCRequest {
  jsonrpc: string;
  id: string | number | null;
  method: string;
  params?: Record<string, unknown>;
}

interface RPCResponse {
  jsonrpc: string;
  id: string | number | null;
  result?: unknown;
  error?: { code: number; message: string; data?: unknown };
}

/** MCP JSON-RPC 2.0 server for AI agent interaction. */
export class MCPServer {
  private readonly tools = new Map<string, MCPTool>();
  private readonly handlers = new Map<string, MCPToolHandler>();
  private readonly serverName: string;
  private readonly serverVersion: string;

  constructor(name: string, version: string) {
    this.serverName = name;
    this.serverVersion = version;
  }

  registerTool(tool: MCPTool, handler: MCPToolHandler): void {
    this.tools.set(tool.name, tool);
    this.handlers.set(tool.name, handler);
  }

  async handleRequest(reqBody: string): Promise<string> {
    let req: RPCRequest;
    try {
      req = JSON.parse(reqBody);
    } catch {
      return this.errorResponse(null, -32700, 'Parse error');
    }

    switch (req.method) {
      case 'initialize':
        return this.successResponse(req.id, {
          protocolVersion: '2024-11-05',
          capabilities: { tools: {} },
          serverInfo: { name: this.serverName, version: this.serverVersion },
        });

      case 'tools/list':
        return this.successResponse(req.id, { tools: [...this.tools.values()] });

      case 'tools/call': {
        const params = req.params as { name: string; arguments?: Record<string, unknown> } | undefined;
        if (!params?.name) return this.errorResponse(req.id, -32602, 'Missing tool name');

        const handler = this.handlers.get(params.name);
        if (!handler) return this.errorResponse(req.id, -32602, `Unknown tool: ${params.name}`);

        try {
          const result = await handler(params.arguments ?? {});
          return this.successResponse(req.id, {
            content: [{ type: 'text', text: JSON.stringify(result) }],
          });
        } catch (err) {
          return this.errorResponse(req.id, -32000, String(err));
        }
      }

      default:
        return this.errorResponse(req.id, -32601, `Method not found: ${req.method}`);
    }
  }

  private successResponse(id: string | number | null, result: unknown): string {
    const resp: RPCResponse = { jsonrpc: '2.0', id, result };
    return JSON.stringify(resp);
  }

  private errorResponse(id: string | number | null, code: number, message: string): string {
    const resp: RPCResponse = { jsonrpc: '2.0', id, error: { code, message } };
    return JSON.stringify(resp);
  }
}
