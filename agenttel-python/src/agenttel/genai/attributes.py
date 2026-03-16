"""GenAI semantic attributes following OTel GenAI conventions."""

# Standard OTel GenAI semantic conventions
OPERATION_NAME = "gen_ai.operation.name"
SYSTEM = "gen_ai.system"
REQUEST_MODEL = "gen_ai.request.model"
REQUEST_TEMPERATURE = "gen_ai.request.temperature"
REQUEST_MAX_TOKENS = "gen_ai.request.max_tokens"
REQUEST_TOP_P = "gen_ai.request.top_p"
REQUEST_STOP_SEQUENCES = "gen_ai.request.stop_sequences"
REQUEST_FREQUENCY_PENALTY = "gen_ai.request.frequency_penalty"
REQUEST_PRESENCE_PENALTY = "gen_ai.request.presence_penalty"

RESPONSE_MODEL = "gen_ai.response.model"
RESPONSE_FINISH_REASONS = "gen_ai.response.finish_reasons"
RESPONSE_ID = "gen_ai.response.id"

USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens"
USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens"
USAGE_TOTAL_TOKENS = "gen_ai.usage.total_tokens"

# Extended usage (reasoning, cached)
USAGE_REASONING_TOKENS = "gen_ai.usage.reasoning_tokens"
USAGE_CACHED_TOKENS = "gen_ai.usage.cached_tokens"

# AgentTel GenAI extensions
FRAMEWORK = "agenttel.genai.framework"
COST_USD = "agenttel.genai.cost_usd"
CACHE_HIT = "agenttel.genai.cache_hit"
RAG_ENABLED = "agenttel.genai.rag_enabled"
RAG_SOURCE_COUNT = "agenttel.genai.rag_source_count"
RAG_RELEVANCE_SCORE = "agenttel.genai.rag_relevance_score"
GUARDRAIL_TRIGGERED = "agenttel.genai.guardrail_triggered"
GUARDRAIL_NAME = "agenttel.genai.guardrail_name"
GUARDRAIL_ACTION = "agenttel.genai.guardrail_action"

# Embedding-specific
EMBEDDING_DIMENSIONS = "gen_ai.embedding.dimensions"
EMBEDDING_VECTOR_COUNT = "gen_ai.embedding.vector_count"

# Retrieval-specific
RETRIEVAL_SOURCE = "gen_ai.retrieval.source"
RETRIEVAL_DOCUMENT_COUNT = "gen_ai.retrieval.document_count"
