import { McpClient } from '../api/mcp-client';
import type { FeedbackEvent } from './types';

/**
 * Dispatch feedback events to the Instrumentation MCP server for action.
 */
export async function applyFeedbackEvent(
  instrumentClient: McpClient,
  event: FeedbackEvent,
): Promise<string> {
  try {
    const result = await instrumentClient.callTool('suggest_improvements', {
      config_path: './agenttel.yml',
      trigger: event.trigger,
      target: event.target,
    });
    return result;
  } catch (e) {
    return `Failed to apply: ${e instanceof Error ? e.message : 'Unknown error'}`;
  }
}

/**
 * Run full validation via the Instrumentation MCP server.
 */
export async function runValidation(
  instrumentClient: McpClient,
  configPath: string,
  sourceDir?: string,
): Promise<string> {
  const args: Record<string, string> = { config_path: configPath };
  if (sourceDir) args.source_dir = sourceDir;

  try {
    return await instrumentClient.callTool('validate_instrumentation', args);
  } catch (e) {
    return `Validation failed: ${e instanceof Error ? e.message : 'Unknown error'}`;
  }
}
