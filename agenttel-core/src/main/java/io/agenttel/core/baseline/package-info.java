/**
 * Baseline providers for establishing what "normal" looks like per operation.
 *
 * <ul>
 *   <li>{@link io.agenttel.core.baseline.StaticBaselineProvider} — From annotation metadata</li>
 *   <li>{@link io.agenttel.core.baseline.RollingBaselineProvider} — Sliding window from observed traffic</li>
 *   <li>{@link io.agenttel.core.baseline.CompositeBaselineProvider} — Chains multiple providers with fallback</li>
 * </ul>
 */
package io.agenttel.core.baseline;
