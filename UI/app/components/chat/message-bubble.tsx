import { AlertTriangleIcon, CheckIcon, CopyIcon, DatabaseZapIcon, SparklesIcon, UserIcon } from "lucide-react";
import { useState } from "react";

import { MarkdownText } from "~/lib/markdown";
import { Badge } from "~/components/ui/badge";
import { Button } from "~/components/ui/button";
import type { ChatSource } from "~/lib/api/types";
import { cn } from "~/lib/utils";

export interface MessageBubbleProps {
  role: "USER" | "ASSISTANT" | "SYSTEM";
  content: string;
  model?: string | null;
  latencyMs?: number | null;
  fromCache?: boolean;
  sources?: ChatSource[];
  pending?: boolean;
  failed?: boolean;
}

export function MessageBubble({
  role,
  content,
  model,
  latencyMs,
  fromCache,
  sources,
  pending,
  failed,
}: MessageBubbleProps) {
  if (role === "USER") {
    return (
      <div className="flex justify-end gap-3">
        <div
          className={cn(
            "max-w-[85%] rounded-2xl rounded-br-sm bg-primary px-4 py-2.5 text-primary-foreground shadow-xs",
            pending && "opacity-70",
          )}
        >
          <p className="text-sm whitespace-pre-wrap">{content}</p>
        </div>
        <div className="mt-1 hidden size-7 shrink-0 items-center justify-center rounded-full bg-muted sm:flex">
          <UserIcon className="size-3.5 text-muted-foreground" />
        </div>
      </div>
    );
  }

  return (
    <div className="flex gap-3">
      <div
        className={cn(
          "mt-1 hidden size-7 shrink-0 items-center justify-center rounded-full sm:flex",
          failed ? "bg-destructive/10 text-destructive" : "bg-primary/10 text-primary",
        )}
      >
        {failed ? <AlertTriangleIcon className="size-3.5" /> : <SparklesIcon className="size-3.5" />}
      </div>

      <div className="min-w-0 flex-1 space-y-2">
        <div
          className={cn(
            "rounded-2xl rounded-tl-sm border bg-card px-4 py-3 shadow-xs",
            failed && "border-destructive/40 bg-destructive/5",
          )}
        >
          {pending ? <ThinkingDots /> : <MarkdownText>{content}</MarkdownText>}
        </div>

        {(model || latencyMs || fromCache || (sources && sources.length > 0)) && !pending && (
          <div className="flex flex-wrap items-center gap-1.5">
            {fromCache && (
              <Badge variant="secondary">
                <DatabaseZapIcon />
                cached answer
              </Badge>
            )}
            {model && <Badge variant="outline">{model}</Badge>}
            {typeof latencyMs === "number" && <Badge variant="outline">{latencyMs} ms</Badge>}
            {sources?.map((source, index) => (
              <Badge key={`${source.documentId ?? source.title}-${index}`} variant="outline" title="Retrieved source">
                {source.title}
                {typeof source.score === "number" ? ` · ${source.score.toFixed(2)}` : ""}
              </Badge>
            ))}
          </div>
        )}

        {!pending && <CopyAnswer content={content} />}
      </div>
    </div>
  );
}

function ThinkingDots() {
  return (
    <div className="flex items-center gap-1.5 py-0.5" aria-label="Generating an answer">
      <span className="size-1.5 animate-bounce rounded-full bg-muted-foreground [animation-delay:-0.3s]" />
      <span className="size-1.5 animate-bounce rounded-full bg-muted-foreground [animation-delay:-0.15s]" />
      <span className="size-1.5 animate-bounce rounded-full bg-muted-foreground" />
      <span className="ml-2 text-xs text-muted-foreground">searching the project's documents…</span>
    </div>
  );
}

function CopyAnswer({ content }: { content: string }) {
  const [copied, setCopied] = useState(false);

  return (
    <Button
      size="sm"
      variant="ghost"
      className="h-6 px-2 text-[11px] text-muted-foreground"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(content);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        } catch {
          setCopied(false);
        }
      }}
    >
      {copied ? <CheckIcon data-icon="inline-start" /> : <CopyIcon data-icon="inline-start" />}
      {copied ? "Copied" : "Copy"}
    </Button>
  );
}
