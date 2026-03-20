import { describe, it, expect } from 'vitest';
import { MCPServer } from '../src/agent/mcp/server.js';

describe('MCPServer', () => {
  it('returns initialize response with protocol version and capabilities', async () => {
    const server = new MCPServer('agenttel-test', '1.0.0');

    const resp = await server.handleRequest(
      JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'initialize' }),
    );
    const parsed = JSON.parse(resp);

    expect(parsed.jsonrpc).toBe('2.0');
    expect(parsed.id).toBe(1);
    expect(parsed.error).toBeUndefined();
    expect(parsed.result.protocolVersion).toBe('2024-11-05');
    expect(parsed.result.capabilities).toEqual({ tools: {} });
    expect(parsed.result.serverInfo).toEqual({ name: 'agenttel-test', version: '1.0.0' });
  });

  it('returns empty tools list', async () => {
    const server = new MCPServer('test', '1.0');

    const resp = await server.handleRequest(
      JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list' }),
    );
    const parsed = JSON.parse(resp);

    expect(parsed.result.tools).toEqual([]);
  });

  it('returns registered tools in tools/list', async () => {
    const server = new MCPServer('test', '1.0');

    server.registerTool(
      { name: 'get_health', description: 'Get service health', inputSchema: { type: 'object' } },
      async () => ({ status: 'ok' }),
    );
    server.registerTool(
      { name: 'get_metrics', description: 'Get metrics', inputSchema: { type: 'object' } },
      async () => ({}),
    );

    const resp = await server.handleRequest(
      JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list' }),
    );
    const parsed = JSON.parse(resp);

    expect(parsed.result.tools).toHaveLength(2);
    const names = parsed.result.tools.map((t: any) => t.name);
    expect(names).toContain('get_health');
    expect(names).toContain('get_metrics');
  });

  it('calls tool and returns result', async () => {
    const server = new MCPServer('test', '1.0');

    server.registerTool(
      { name: 'greet', description: 'Greet someone', inputSchema: { type: 'object' } },
      async (params) => ({ greeting: `Hello, ${params.name}` }),
    );

    const resp = await server.handleRequest(
      JSON.stringify({
        jsonrpc: '2.0',
        id: 1,
        method: 'tools/call',
        params: { name: 'greet', arguments: { name: 'Alice' } },
      }),
    );
    const parsed = JSON.parse(resp);

    expect(parsed.error).toBeUndefined();
    expect(parsed.result.content).toHaveLength(1);
    expect(parsed.result.content[0].type).toBe('text');

    const text = JSON.parse(parsed.result.content[0].text);
    expect(text.greeting).toBe('Hello, Alice');
  });

  it('returns error for unknown tool', async () => {
    const server = new MCPServer('test', '1.0');

    const resp = await server.handleRequest(
      JSON.stringify({
        jsonrpc: '2.0',
        id: 1,
        method: 'tools/call',
        params: { name: 'nonexistent', arguments: {} },
      }),
    );
    const parsed = JSON.parse(resp);

    expect(parsed.error).toBeDefined();
    expect(parsed.error.code).toBe(-32602);
    expect(parsed.error.message).toContain('Unknown tool');
  });

  it('returns error for unknown method', async () => {
    const server = new MCPServer('test', '1.0');

    const resp = await server.handleRequest(
      JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'unknown/method' }),
    );
    const parsed = JSON.parse(resp);

    expect(parsed.error).toBeDefined();
    expect(parsed.error.code).toBe(-32601);
    expect(parsed.error.message).toContain('Method not found');
  });

  it('returns parse error for invalid JSON', async () => {
    const server = new MCPServer('test', '1.0');

    const resp = await server.handleRequest('{invalid json');
    const parsed = JSON.parse(resp);

    expect(parsed.error).toBeDefined();
    expect(parsed.error.code).toBe(-32700);
    expect(parsed.error.message).toBe('Parse error');
  });
});
