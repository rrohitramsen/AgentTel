/**
 * Client-side feedback event detection.
 * Analyzes MCP health/SLO data to find coverage gaps and improvement opportunities.
 */
export function detectFeedbackEvents(healthRaw, sloRaw) {
    const events = [];
    // Detect TODO baselines from health data
    if (healthRaw.includes('TODO') || healthRaw.includes('no baseline')) {
        events.push({
            trigger: 'missing_baseline',
            riskLevel: 'low',
            target: 'operations with TODO baselines',
            reasoning: 'Some operations have placeholder baselines. Deploy and observe traffic to fill them.',
            autoApplicable: true,
        });
    }
    // Detect high error rates suggesting misconfigured baselines
    const errorRateMatch = healthRaw.match(/error[_\s]?rate[:\s]+(\d+\.?\d*)%/i);
    if (errorRateMatch) {
        const rate = parseFloat(errorRateMatch[1]);
        if (rate > 5) {
            events.push({
                trigger: 'slo_burn_rate_high',
                riskLevel: 'medium',
                target: 'service-wide',
                currentValue: `${rate}% error rate`,
                reasoning: `Error rate of ${rate}% is elevated. SLO baselines may need recalibration.`,
                autoApplicable: false,
            });
        }
    }
    // Detect missing runbook references
    if (healthRaw.includes('no runbook') || healthRaw.includes('runbook: none')) {
        events.push({
            trigger: 'missing_runbook',
            riskLevel: 'medium',
            target: 'operations without runbooks',
            reasoning: 'Some operations have no runbook URL. AI agents cannot look up resolution steps.',
            autoApplicable: false,
        });
    }
    // Detect dependencies without health checks
    if (healthRaw.includes('health_check: TODO') || healthRaw.includes('no health check')) {
        events.push({
            trigger: 'uncovered_endpoint',
            riskLevel: 'medium',
            target: 'dependencies without health checks',
            reasoning: 'Some dependencies have no health check URL configured.',
            autoApplicable: false,
        });
    }
    // If no specific issues detected, check for general coverage
    if (events.length === 0) {
        events.push({
            trigger: 'missing_baseline',
            riskLevel: 'low',
            target: 'general',
            reasoning: 'No specific gaps detected. Run validate_instrumentation for deeper analysis.',
            autoApplicable: false,
        });
    }
    return events;
}
