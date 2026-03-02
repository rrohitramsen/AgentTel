import { config } from '../config';
export async function searchTraces(service, options) {
    const params = new URLSearchParams({
        service,
        limit: String(options?.limit ?? 10),
        lookback: '1h',
    });
    if (options?.operation)
        params.set('operation', options.operation);
    try {
        const res = await fetch(`${config.jaegerApiUrl}/traces?${params}`);
        if (!res.ok)
            return [];
        const json = await res.json();
        return json.data || [];
    }
    catch {
        return [];
    }
}
export async function getServices() {
    try {
        const res = await fetch(`${config.jaegerApiUrl}/services`);
        if (!res.ok)
            return [];
        const json = await res.json();
        return json.data || [];
    }
    catch {
        return [];
    }
}
