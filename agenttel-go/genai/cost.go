package genai

// modelPricing holds per-million-token pricing for known models.
var modelPricing = map[string][2]float64{
	// OpenAI
	"gpt-4o":            {2.50, 10.00},
	"gpt-4o-mini":       {0.15, 0.60},
	"gpt-4-turbo":       {10.00, 30.00},
	"gpt-4":             {30.00, 60.00},
	"gpt-3.5-turbo":     {0.50, 1.50},
	"o1":                {15.00, 60.00},
	"o1-mini":           {3.00, 12.00},
	"o1-pro":            {150.00, 600.00},
	"o3":                {10.00, 40.00},
	"o3-mini":           {1.10, 4.40},
	// Anthropic
	"claude-opus-4":           {15.00, 75.00},
	"claude-sonnet-4":         {3.00, 15.00},
	"claude-3-5-sonnet":       {3.00, 15.00},
	"claude-3-5-haiku":        {0.80, 4.00},
	"claude-3-opus":           {15.00, 75.00},
	"claude-3-haiku":          {0.25, 1.25},
	// Google
	"gemini-2.0-flash":        {0.10, 0.40},
	"gemini-1.5-pro":          {1.25, 5.00},
	"gemini-1.5-flash":        {0.075, 0.30},
}

// CalculateCost returns the estimated USD cost for a model invocation.
func CalculateCost(model string, inputTokens, outputTokens int64) float64 {
	pricing, ok := modelPricing[model]
	if !ok {
		return 0
	}
	inputCost := float64(inputTokens) / 1_000_000 * pricing[0]
	outputCost := float64(outputTokens) / 1_000_000 * pricing[1]
	return inputCost + outputCost
}

// RegisterModelPricing adds or updates pricing for a model.
// Pricing is [inputPerMillion, outputPerMillion] in USD.
func RegisterModelPricing(model string, inputPerMillion, outputPerMillion float64) {
	modelPricing[model] = [2]float64{inputPerMillion, outputPerMillion}
}
