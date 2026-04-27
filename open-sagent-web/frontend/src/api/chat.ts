/**
 * SSE流式对话API
 *
 * 实际调用在 useChatStream hook 中通过原生 EventSource 实现
 * 此文件仅保留类型定义和辅助函数
 */

export function buildStreamUrl(sessionId: string, message: string, agentVersion: string): string {
  const params = new URLSearchParams({ sessionId, message, agentVersion });
  return `/api/chat/stream?${params.toString()}`;
}
