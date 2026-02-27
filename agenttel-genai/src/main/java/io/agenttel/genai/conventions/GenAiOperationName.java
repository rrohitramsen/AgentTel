package io.agenttel.genai.conventions;

/**
 * Standard GenAI operation name constants following OTel semantic conventions.
 */
public final class GenAiOperationName {
    private GenAiOperationName() {}

    public static final String CHAT = "chat";
    public static final String TEXT_COMPLETION = "text_completion";
    public static final String EMBEDDINGS = "embeddings";
    public static final String RETRIEVE = "retrieve";
    public static final String EXECUTE_TOOL = "execute_tool";
}
