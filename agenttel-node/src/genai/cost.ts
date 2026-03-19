// Per-million-token pricing: [inputPerMillion, outputPerMillion]
const modelPricing = new Map<string, [number, number]>([
  // OpenAI
  ['gpt-4o', [2.50, 10.00]],
  ['gpt-4o-mini', [0.15, 0.60]],
  ['gpt-4-turbo', [10.00, 30.00]],
  ['gpt-4', [30.00, 60.00]],
  ['gpt-3.5-turbo', [0.50, 1.50]],
  ['o1', [15.00, 60.00]],
  ['o1-mini', [3.00, 12.00]],
  ['o1-pro', [150.00, 600.00]],
  ['o3', [10.00, 40.00]],
  ['o3-mini', [1.10, 4.40]],
  // Anthropic
  ['claude-opus-4', [15.00, 75.00]],
  ['claude-sonnet-4', [3.00, 15.00]],
  ['claude-3-5-sonnet', [3.00, 15.00]],
  ['claude-3-5-haiku', [0.80, 4.00]],
  ['claude-3-opus', [15.00, 75.00]],
  ['claude-3-haiku', [0.25, 1.25]],
  // Google
  ['gemini-2.0-flash', [0.10, 0.40]],
  ['gemini-1.5-pro', [1.25, 5.00]],
  ['gemini-1.5-flash', [0.075, 0.30]],
]);

/** Calculate estimated USD cost for a model invocation. */
export function calculateCost(model: string, inputTokens: number, outputTokens: number): number {
  const pricing = modelPricing.get(model);
  if (!pricing) return 0;
  return (inputTokens / 1_000_000) * pricing[0] + (outputTokens / 1_000_000) * pricing[1];
}

/** Register or update pricing for a model. */
export function registerModelPricing(model: string, inputPerMillion: number, outputPerMillion: number): void {
  modelPricing.set(model, [inputPerMillion, outputPerMillion]);
}
