/**
 * 对流式输出的 Markdown 内容进行预处理，自动补全未闭合的语法结构，
 * 使 react-markdown 能够正确解析和实时渲染。
 *
 * 主要处理：
 * 1. 未闭合的代码块（```）：补充闭合标记
 * 2. 未闭合的行内代码（`）：补充闭合标记
 */
export function normalizeStreamingMarkdown(content: string): string {
  if (!content || content.trim() === '') {
    return content;
  }

  let normalized = content;

  // 1. 处理未闭合的代码块
  // 统计 ``` 出现的次数，奇数表示存在未闭合的代码块
  const fenceMatches = normalized.match(/```/g);
  if (fenceMatches && fenceMatches.length % 2 === 1) {
    // 补充换行和闭合标记，使代码块能够正确渲染
    if (!normalized.endsWith('\n')) {
      normalized += '\n';
    }
    normalized += '```';
  }

  // 2. 处理未闭合的行内代码
  // 统计单个反引号（排除三个一组的代码块标记）
  const backtickMatches = normalized.match(/(?<!`)`(?!`)/g);
  if (backtickMatches && backtickMatches.length % 2 === 1) {
    normalized += '`';
  }

  return normalized;
}
