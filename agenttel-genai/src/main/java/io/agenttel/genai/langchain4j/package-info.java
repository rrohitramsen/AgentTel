/**
 * LangChain4j instrumentation — decorator-based tracing for chat models, embedding models,
 * and RAG content retrievers.
 *
 * <p>Usage:
 * <pre>{@code
 * ChatLanguageModel traced = LangChain4jInstrumentation.instrument(model, otel, "gpt-4o", "openai");
 * }</pre>
 */
package io.agenttel.genai.langchain4j;
