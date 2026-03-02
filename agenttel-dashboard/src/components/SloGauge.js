import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { colors, statusColors, font, spacing, radii } from '../styles/theme';
export function SloGauge({ name, budgetPercent, burnRate, status }) {
    const radius = 28;
    const circumference = 2 * Math.PI * radius;
    const filled = Math.max(0, Math.min(100, budgetPercent));
    const offset = circumference - (filled / 100) * circumference;
    const color = statusColors[status.toLowerCase()] || colors.textMuted;
    return (_jsxs("div", { className: "card-hover", style: {
            padding: spacing.lg,
            backgroundColor: colors.surface,
            border: `1px solid ${colors.border}`,
            borderRadius: radii.md,
            display: 'flex',
            alignItems: 'center',
            gap: spacing.lg,
        }, children: [_jsxs("svg", { width: "64", height: "64", viewBox: "0 0 64 64", children: [_jsx("circle", { cx: "32", cy: "32", r: radius, fill: "none", stroke: "rgba(255,255,255,0.06)", strokeWidth: "5" }), _jsx("circle", { cx: "32", cy: "32", r: radius, fill: "none", stroke: color, strokeWidth: "5", strokeDasharray: circumference, strokeDashoffset: offset, strokeLinecap: "round", transform: "rotate(-90 32 32)", style: { transition: 'stroke-dashoffset 0.6s ease' } }), _jsxs("text", { x: "32", y: "32", textAnchor: "middle", dominantBaseline: "central", fill: colors.text, fontSize: "11", fontWeight: "700", fontFamily: font.mono, children: [filled.toFixed(0), "%"] })] }), _jsxs("div", { children: [_jsx("div", { style: { fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }, children: name }), _jsxs("div", { style: { fontSize: font.size.xs, color: colors.textMuted, marginTop: '2px' }, children: ["Burn rate: ", _jsxs("span", { style: { color, fontWeight: font.weight.semibold }, children: [burnRate.toFixed(1), "x"] })] })] })] }));
}
