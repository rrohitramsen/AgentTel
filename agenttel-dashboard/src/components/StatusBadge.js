import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { statusColors, colors } from '../styles/theme';
export function StatusBadge({ status, size = 'md' }) {
    const normalized = status.toLowerCase();
    const color = statusColors[normalized] || colors.textMuted;
    const dotSize = size === 'sm' ? '7px' : '9px';
    const fontSize = size === 'sm' ? '10px' : '11px';
    const isCritical = normalized === 'critical' || normalized === 'error' || normalized === 'violated';
    return (_jsxs("span", { style: { display: 'inline-flex', alignItems: 'center', gap: '6px' }, children: [_jsx("span", { className: isCritical ? 'status-pulse' : undefined, style: {
                    width: dotSize,
                    height: dotSize,
                    borderRadius: '50%',
                    backgroundColor: color,
                    display: 'inline-block',
                    boxShadow: `0 0 6px ${color}`,
                    flexShrink: 0,
                } }), _jsx("span", { style: {
                    fontSize,
                    color,
                    textTransform: 'uppercase',
                    fontWeight: 600,
                    letterSpacing: '0.5px',
                }, children: status })] }));
}
