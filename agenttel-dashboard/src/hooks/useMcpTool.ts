import { useState, useEffect, useRef, useCallback } from 'react';
import { McpClient } from '../api/mcp-client';

interface UseMcpToolResult<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  lastUpdated: Date | null;
  refresh: () => void;
}

/**
 * Generic hook for polling an MCP tool at a configurable interval.
 */
export function useMcpTool<T>(
  client: McpClient,
  toolName: string,
  args: Record<string, string>,
  intervalMs: number,
  parser: (raw: string) => T,
): UseMcpToolResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const raw = await client.callTool(toolName, args);
      const parsed = parser(raw);
      setData(parsed);
      setError(null);
      setLastUpdated(new Date());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  }, [client, toolName, JSON.stringify(args), parser]);

  useEffect(() => {
    fetchData();
    intervalRef.current = setInterval(fetchData, intervalMs);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [fetchData, intervalMs]);

  const refresh = useCallback(() => {
    setLoading(true);
    fetchData();
  }, [fetchData]);

  return { data, loading, error, lastUpdated, refresh };
}
