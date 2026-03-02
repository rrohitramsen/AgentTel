import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { colors, font, spacing, card as cardStyle } from '../styles/theme';
const trendArrows = {
    up: '\u2197',
    down: '\u2198',
    stable: '\u2192',
};
const trendColors = {
    up: colors.success,
    down: colors.error,
    stable: colors.textMuted,
};
export function MetricCard({ label, value, trend, changePercent, color, subtitle, children }) {
    return (_jsxs("div", { className: "card-hover", style: cardStyle, children: [_jsxs("div", { style: {
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    marginBottom: spacing.sm,
                }, children: [_jsx("div", { style: { fontSize: font.size.sm, color: colors.textMuted, textTransform: 'uppercase', letterSpacing: '0.3px' }, children: label }), trend && (_jsxs("div", { style: {
                            fontSize: font.size.sm,
                            color: trendColors[trend],
                            fontWeight: font.weight.semibold,
                            display: 'flex',
                            alignItems: 'center',
                            gap: '2px',
                        }, children: [trendArrows[trend], changePercent !== undefined && ` ${Math.abs(changePercent).toFixed(1)}%`] }))] }), _jsx("div", { style: {
                    fontSize: font.size['3xl'],
                    fontWeight: font.weight.bold,
                    color: color || colors.text,
                    fontFamily: font.mono,
                    lineHeight: 1.2,
                }, children: value }), subtitle && (_jsx("div", { style: { fontSize: font.size.xs, color: colors.textDim, marginTop: spacing.xs }, children: subtitle })), children && _jsx("div", { style: { marginTop: spacing.md }, children: children })] }));
}
