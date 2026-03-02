import { Routes, Route, Navigate } from 'react-router-dom';
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
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<CommandCenter />} />
        <Route path="/fleet" element={<FleetOverview />} />
        <Route path="/slo" element={<SloCompliance />} />
        <Route path="/summary" element={<ExecutiveSummary />} />
        <Route path="/trends" element={<TrendAnalysis />} />
        <Route path="/monitor" element={<MonitorDecisions />} />
        <Route path="/incidents" element={<IncidentContext />} />
        <Route path="/cross-stack" element={<CrossStackView />} />
        <Route path="/gaps" element={<CoverageGaps />} />
        <Route path="/suggestions" element={<Suggestions />} />
        <Route path="/traffic" element={<TrafficGenerator />} />
        <Route path="/agents" element={<Agents />} />
      </Route>
    </Routes>
  );
}
