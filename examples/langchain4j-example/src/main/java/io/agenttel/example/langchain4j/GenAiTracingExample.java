package io.agenttel.example.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.agenttel.genai.langchain4j.LangChain4jInstrumentation;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.util.List;

/**
 * Example demonstrating AgentTel GenAI tracing with LangChain4j.
 *
 * <p>This example uses a mock ChatLanguageModel to show how AgentTel
 * automatically creates spans with gen_ai.* attributes and cost calculation.
 *
 * <p>In a real application, replace {@link MockChatModel} with your actual
 * LangChain4j model (e.g., OpenAiChatModel, AnthropicChatModel).
 */
public class GenAiTracingExample {

    public static void main(String[] args) {
        // 1. Set up OpenTelemetry with console exporter
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                .build();

        OpenTelemetry otel = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        // 2. Create a mock LLM model (replace with real model in production)
        ChatLanguageModel mockModel = new MockChatModel();

        // 3. Wrap with AgentTel tracing
        ChatLanguageModel tracedModel = LangChain4jInstrumentation.instrument(
                mockModel, otel, "gpt-4o", "openai");

        // 4. Use the model normally — spans are created automatically
        System.out.println("=== AgentTel GenAI Tracing Example ===\n");

        System.out.println("Sending chat request...");
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("What is observability?")))
                .build();
        ChatResponse response = tracedModel.chat(request);
        System.out.println("Response: " + response.aiMessage().text());

        System.out.println("\nSending another request...");
        ChatRequest request2 = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Explain OpenTelemetry in one sentence.")))
                .build();
        ChatResponse response2 = tracedModel.chat(request2);
        System.out.println("Response: " + response2.aiMessage().text());

        System.out.println("\n=== Check console output above for span details ===");
        System.out.println("Look for: gen_ai.operation.name, gen_ai.system, agenttel.genai.cost_usd");

        // 5. Cleanup
        tracerProvider.close();
    }

    /**
     * Mock ChatLanguageModel that simulates responses with token usage.
     * Replace with a real model in production.
     */
    static class MockChatModel implements ChatLanguageModel {

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            String userMessage = chatRequest.messages().stream()
                    .filter(m -> m instanceof UserMessage)
                    .map(m -> ((UserMessage) m).singleText())
                    .findFirst()
                    .orElse("unknown");

            String reply = "This is a mock response to: " + userMessage;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(reply))
                    .tokenUsage(new TokenUsage(50, 30))
                    .build();
        }

        @Override
        @SuppressWarnings("removal")
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(
                    AiMessage.from("Mock response"),
                    new TokenUsage(50, 30),
                    null
            );
        }
    }
}
