import { useState, useEffect, useRef, useCallback } from 'react';
/**
 * Generic hook for polling an MCP tool at a configurable interval.
 */
export function useMcpTool(client, toolName, args, intervalMs, parser) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [lastUpdated, setLastUpdated] = useState(null);
    const intervalRef = useRef(null);
    const fetchData = useCallback(async () => {
        try {
            const raw = await client.callTool(toolName, args);
            const parsed = parser(raw);
            setData(parsed);
            setError(null);
            setLastUpdated(new Date());
        }
        catch (e) {
            setError(e instanceof Error ? e.message : 'Unknown error');
        }
        finally {
            setLoading(false);
        }
    }, [client, toolName, JSON.stringify(args), parser]);
    useEffect(() => {
        fetchData();
        intervalRef.current = setInterval(fetchData, intervalMs);
        return () => {
            if (intervalRef.current)
                clearInterval(intervalRef.current);
        };
    }, [fetchData, intervalMs]);
    const refresh = useCallback(() => {
        setLoading(true);
        fetchData();
    }, [fetchData]);
    return { data, loading, error, lastUpdated, refresh };
}
