package io.agenttel.genai.conventions;

import io.opentelemetry.api.common.AttributeKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiAttributesTest {

    @Test
    void standardAttributeKeysHaveCorrectNames() {
        assertThat(GenAiAttributes.GEN_AI_OPERATION_NAME.getKey()).isEqualTo("gen_ai.operation.name");
        assertThat(GenAiAttributes.GEN_AI_SYSTEM.getKey()).isEqualTo("gen_ai.system");
        assertThat(GenAiAttributes.GEN_AI_REQUEST_MODEL.getKey()).isEqualTo("gen_ai.request.model");
        assertThat(GenAiAttributes.GEN_AI_RESPONSE_MODEL.getKey()).isEqualTo("gen_ai.response.model");
        assertThat(GenAiAttributes.GEN_AI_RESPONSE_ID.getKey()).isEqualTo("gen_ai.response.id");
        assertThat(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS.getKey()).isEqualTo("gen_ai.usage.input_tokens");
        assertThat(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS.getKey()).isEqualTo("gen_ai.usage.output_tokens");
    }

    @Test
    void requestParameterAttributeKeysHaveCorrectTypes() {
        assertThat(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE.getType())
                .isEqualTo(AttributeKey.doubleKey("test").getType());
        assertThat(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS.getType())
                .isEqualTo(AttributeKey.longKey("test").getType());
        assertThat(GenAiAttributes.GEN_AI_REQUEST_TOP_P.getType())
                .isEqualTo(AttributeKey.doubleKey("test").getType());
    }

    @Test
    void agentTelGenAiAttributeKeysHaveCorrectNames() {
        assertThat(AgentTelGenAiAttributes.GENAI_FRAMEWORK.getKey()).isEqualTo("agenttel.genai.framework");
        assertThat(AgentTelGenAiAttributes.GENAI_COST_USD.getKey()).isEqualTo("agenttel.genai.cost_usd");
        assertThat(AgentTelGenAiAttributes.GENAI_RAG_SOURCE_COUNT.getKey()).isEqualTo("agenttel.genai.rag_source_count");
        assertThat(AgentTelGenAiAttributes.GENAI_RAG_RELEVANCE_SCORE_AVG.getKey()).isEqualTo("agenttel.genai.rag_relevance_score_avg");
        assertThat(AgentTelGenAiAttributes.GENAI_GUARDRAIL_TRIGGERED.getKey()).isEqualTo("agenttel.genai.guardrail_triggered");
        assertThat(AgentTelGenAiAttributes.GENAI_CACHE_HIT.getKey()).isEqualTo("agenttel.genai.cache_hit");
    }

    @Test
    void operationNameConstantsAreCorrect() {
        assertThat(GenAiOperationName.CHAT).isEqualTo("chat");
        assertThat(GenAiOperationName.TEXT_COMPLETION).isEqualTo("text_completion");
        assertThat(GenAiOperationName.EMBEDDINGS).isEqualTo("embeddings");
        assertThat(GenAiOperationName.RETRIEVE).isEqualTo("retrieve");
        assertThat(GenAiOperationName.EXECUTE_TOOL).isEqualTo("execute_tool");
    }
}
