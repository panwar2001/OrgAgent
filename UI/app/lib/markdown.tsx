import Markdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";

import { cn } from "~/lib/utils";

interface MarkdownTextProps {
  children: string;
  className?: string;
}

/**
 * Renders an assistant answer.
 *
 * Answers are markdown: headings, lists, tables, links and fenced code with syntax highlighting.
 * Retrieval answers cite documents, so tables and lists are common and have to survive rendering.
 * `prose` styles come from the typography plugin, tuned to the shadcn tokens.
 */
export function MarkdownText({ children, className }: MarkdownTextProps) {
  return (
    <div
      className={cn(
        "prose prose-sm max-w-none dark:prose-invert",
        "prose-headings:font-heading prose-headings:scroll-mt-20",
        "prose-p:leading-relaxed prose-p:my-2",
        "prose-pre:my-3 prose-pre:rounded-lg prose-pre:border prose-pre:bg-muted prose-pre:p-3 prose-pre:text-[13px]",
        "prose-code:rounded prose-code:bg-muted prose-code:px-1 prose-code:py-0.5 prose-code:text-[0.85em] prose-code:font-normal",
        "prose-code:before:content-none prose-code:after:content-none",
        "prose-pre:prose-code:bg-transparent prose-pre:prose-code:p-0",
        "prose-table:text-sm prose-th:text-left",
        "prose-a:text-primary prose-a:underline prose-a:underline-offset-2",
        "prose-blockquote:border-l-primary/40 prose-blockquote:not-italic prose-blockquote:text-muted-foreground",
        "prose-hr:border-border",
        "prose-strong:font-semibold prose-strong:text-foreground",
        className,
      )}
    >
      <Markdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeHighlight, { detect: true, ignoreMissing: true }]]}
      >
        {children}
      </Markdown>
    </div>
  );
}
