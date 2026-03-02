import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { colors } from '../styles/theme';
export function TrendChart({ data, width = 200, height = 60, color = colors.primaryLight, label, showGradient = true, }) {
    if (data.length < 2) {
        return (_jsx("div", { style: {
                width,
                height,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
            }, children: _jsx("span", { style: { color: colors.textDim, fontSize: '11px' }, children: "No data" }) }));
    }
    const svgWidth = typeof width === 'number' ? width : 200;
    const values = data.map((d) => d.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    const padding = 4;
    const coords = values.map((v, i) => ({
        x: padding + (i / (values.length - 1)) * (svgWidth - padding * 2),
        y: height - padding - ((v - min) / range) * (height - padding * 2),
    }));
    const polylinePoints = coords.map((c) => `${c.x},${c.y}`).join(' ');
    // Build a closed path for gradient fill
    const areaPath = showGradient
        ? `M ${coords[0].x},${height} ` +
            coords.map((c) => `L ${c.x},${c.y}`).join(' ') +
            ` L ${coords[coords.length - 1].x},${height} Z`
        : '';
    const gradientId = `grad-${Math.random().toString(36).slice(2, 8)}`;
    return (_jsxs("div", { children: [label && (_jsx("div", { style: { fontSize: '11px', color: colors.textMuted, marginBottom: '4px' }, children: label })), _jsxs("svg", { width: width, height: height, style: { display: 'block', width: '100%' }, viewBox: `0 0 ${svgWidth} ${height}`, preserveAspectRatio: "none", children: [showGradient && (_jsxs(_Fragment, { children: [_jsx("defs", { children: _jsxs("linearGradient", { id: gradientId, x1: "0", y1: "0", x2: "0", y2: "1", children: [_jsx("stop", { offset: "0%", stopColor: color, stopOpacity: "0.2" }), _jsx("stop", { offset: "100%", stopColor: color, stopOpacity: "0" })] }) }), _jsx("path", { d: areaPath, fill: `url(#${gradientId})` })] })), _jsx("polyline", { points: polylinePoints, fill: "none", stroke: color, strokeWidth: "1.5", strokeLinejoin: "round", strokeLinecap: "round" })] })] }));
}
