import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { NavLink, Outlet } from 'react-router-dom';
import { colors, font, spacing, transitions } from '../styles/theme';
import { config } from '../config';
const navSections = [
    {
        label: 'Overview',
        items: [
            { to: '/', label: 'Command Center', icon: '\u25C9' },
            { to: '/fleet', label: 'Fleet Health', icon: '\u229A' },
        ],
    },
    {
        label: 'Reliability',
        items: [
            { to: '/slo', label: 'SLO Compliance', icon: '\u25CE' },
            { to: '/trends', label: 'Trends', icon: '\u2197' },
            { to: '/incidents', label: 'Incidents', icon: '\u26A0' },
        ],
    },
    {
        label: 'Intelligence',
        items: [
            { to: '/monitor', label: 'Monitor Agent', icon: '\u229A' },
            { to: '/cross-stack', label: 'Cross-Stack', icon: '\u2194' },
            { to: '/summary', label: 'Summary', icon: '\u2630' },
        ],
    },
    {
        label: 'Improve',
        items: [
            { to: '/gaps', label: 'Coverage Gaps', icon: '\u25A3' },
            { to: '/suggestions', label: 'Suggestions', icon: '\u2606' },
        ],
    },
    {
        label: 'Traffic',
        items: [
            { to: '/traffic', label: 'Generate Traffic', icon: '\u25B6' },
        ],
    },
    {
        label: 'Management',
        items: [
            { to: '/agents', label: 'Agents', icon: '\u2699' },
        ],
    },
];
export function Layout() {
    return (_jsxs("div", { style: { display: 'flex', minHeight: '100vh', backgroundColor: colors.bg }, children: [_jsxs("nav", { style: {
                    width: '240px',
                    backgroundColor: colors.surface,
                    borderRight: `1px solid ${colors.border}`,
                    flexShrink: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    overflow: 'auto',
                }, children: [_jsx("div", { style: {
                            padding: `${spacing.xl} ${spacing.xl} ${spacing.lg}`,
                            borderBottom: `1px solid ${colors.border}`,
                        }, children: _jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm }, children: [_jsx("div", { style: {
                                        width: '28px',
                                        height: '28px',
                                        borderRadius: '8px',
                                        background: `linear-gradient(135deg, ${colors.primary}, ${colors.primaryLight})`,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        fontSize: '13px',
                                        color: '#fff',
                                    }, children: '\u25C9' }), _jsxs("div", { children: [_jsx("div", { style: { fontSize: font.size.lg, fontWeight: font.weight.bold, color: colors.text, letterSpacing: '-0.01em' }, children: "AgentTel" }), _jsx("div", { style: { fontSize: '10px', color: colors.textDim, letterSpacing: '0.8px', textTransform: 'uppercase' }, children: "Command Center" })] })] }) }), _jsx("div", { style: { padding: `${spacing.md} 0`, flex: 1 }, children: navSections.map((section) => (_jsxs("div", { style: { marginBottom: spacing.sm }, children: [_jsx("div", { style: {
                                        padding: `${spacing.sm} ${spacing.xl}`,
                                        fontSize: '10px',
                                        fontWeight: font.weight.semibold,
                                        color: colors.textDim,
                                        textTransform: 'uppercase',
                                        letterSpacing: '0.8px',
                                    }, children: section.label }), section.items.map((item) => (_jsxs(NavLink, { to: item.to, end: item.to === '/', style: ({ isActive }) => ({
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '10px',
                                        padding: `7px ${spacing.xl}`,
                                        color: isActive ? colors.text : colors.textMuted,
                                        backgroundColor: isActive ? colors.surfaceHover : 'transparent',
                                        textDecoration: 'none',
                                        fontSize: font.size.md,
                                        fontWeight: isActive ? font.weight.medium : font.weight.normal,
                                        borderLeft: isActive
                                            ? `2px solid ${colors.primary}`
                                            : '2px solid transparent',
                                        transition: transitions.fast,
                                    }), children: [_jsx("span", { style: { fontSize: '13px', width: '18px', textAlign: 'center', opacity: 0.7 }, children: item.icon }), item.label] }, item.to)))] }, section.label))) }), _jsx("div", { style: {
                            padding: spacing.lg,
                            borderTop: `1px solid ${colors.border}`,
                        }, children: _jsxs("a", { href: config.jaegerExternalUrl, target: "_blank", rel: "noopener noreferrer", style: {
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                                padding: `${spacing.sm} ${spacing.md}`,
                                color: colors.textMuted,
                                textDecoration: 'none',
                                fontSize: font.size.sm,
                                borderRadius: '8px',
                                transition: transitions.fast,
                                backgroundColor: 'rgba(255,255,255,0.02)',
                                border: `1px solid ${colors.border}`,
                            }, children: [_jsx("span", { style: { fontSize: '13px' }, children: '\u2B95' }), "Traces (Jaeger)", _jsx("span", { style: { marginLeft: 'auto', fontSize: '10px', opacity: 0.4 }, children: '\u2197' })] }) })] }), _jsx("main", { style: {
                    flex: 1,
                    padding: spacing['3xl'],
                    overflow: 'auto',
                    minWidth: 0,
                }, children: _jsx("div", { className: "panel-enter", style: { maxWidth: '1400px' }, children: _jsx(Outlet, {}) }) })] }));
}
