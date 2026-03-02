import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { colors, font, spacing } from '../styles/theme';
export function DataTable({ columns, data, keyField, }) {
    return (_jsx("div", { style: { overflowX: 'auto' }, children: _jsxs("table", { style: {
                width: '100%',
                borderCollapse: 'collapse',
                fontSize: font.size.md,
            }, children: [_jsx("thead", { children: _jsx("tr", { children: columns.map((col) => (_jsx("th", { style: {
                                padding: `${spacing.sm} ${spacing.md}`,
                                textAlign: (col.align || 'left'),
                                color: colors.textDim,
                                borderBottom: `1px solid ${colors.border}`,
                                fontWeight: font.weight.semibold,
                                fontSize: font.size.xs,
                                textTransform: 'uppercase',
                                letterSpacing: '0.5px',
                                width: col.width,
                            }, children: col.header }, col.key))) }) }), _jsx("tbody", { children: data.map((row) => (_jsx("tr", { className: "row-hover", style: {
                            borderBottom: `1px solid ${colors.border}`,
                            transition: 'background-color 0.1s ease',
                        }, children: columns.map((col) => (_jsx("td", { style: {
                                padding: `${spacing.sm} ${spacing.md}`,
                                color: colors.text,
                                textAlign: (col.align || 'left'),
                            }, children: col.render ? col.render(row) : String(row[col.key] ?? '') }, col.key))) }, String(row[keyField])))) })] }) }));
}
