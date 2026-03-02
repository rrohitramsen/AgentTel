import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { FleetOverview } from './panels/FleetOverview';
import { SloCompliance } from './panels/SloCompliance';
import { ExecutiveSummary } from './panels/ExecutiveSummary';
import { TrendAnalysis } from './panels/TrendAnalysis';
import { MonitorDecisions } from './panels/MonitorDecisions';
import { IncidentContext } from './panels/IncidentContext';
import { CrossStackView } from './panels/CrossStackView';
import { CoverageGaps } from './panels/CoverageGaps';
import { Suggestions } from './panels/Suggestions';
import { CommandCenter } from './panels/CommandCenter';
import { TrafficGenerator } from './panels/TrafficGenerator';
import { Agents } from './panels/Agents';
export function App() {
    return (_jsx(Routes, { children: _jsxs(Route, { element: _jsx(Layout, {}), children: [_jsx(Route, { index: true, element: _jsx(CommandCenter, {}) }), _jsx(Route, { path: "/fleet", element: _jsx(FleetOverview, {}) }), _jsx(Route, { path: "/slo", element: _jsx(SloCompliance, {}) }), _jsx(Route, { path: "/summary", element: _jsx(ExecutiveSummary, {}) }), _jsx(Route, { path: "/trends", element: _jsx(TrendAnalysis, {}) }), _jsx(Route, { path: "/monitor", element: _jsx(MonitorDecisions, {}) }), _jsx(Route, { path: "/incidents", element: _jsx(IncidentContext, {}) }), _jsx(Route, { path: "/cross-stack", element: _jsx(CrossStackView, {}) }), _jsx(Route, { path: "/gaps", element: _jsx(CoverageGaps, {}) }), _jsx(Route, { path: "/suggestions", element: _jsx(Suggestions, {}) }), _jsx(Route, { path: "/traffic", element: _jsx(TrafficGenerator, {}) }), _jsx(Route, { path: "/agents", element: _jsx(Agents, {}) })] }) }));
}
