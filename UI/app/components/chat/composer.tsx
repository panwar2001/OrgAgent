import { ArrowUpIcon, Loader2Icon } from "lucide-react";
import { useEffect, useRef } from "react";
import { useFetcher } from "react-router";

import { Button } from "~/components/ui/button";
import { Textarea } from "~/components/ui/textarea";

interface ComposerProps {
  organizationId: string;
  projectId: string;
  conversationId?: string;
  disabled?: boolean;
  placeholder?: string;
}

/**
 * The message box: Enter sends, Shift+Enter adds a line, and the textarea grows with the text up to
 * a limit. While an answer is in flight the send button becomes a spinner and further sends stop.
 */
export function Composer({
  organizationId,
  projectId,
  conversationId,
  disabled,
  placeholder = "Ask about this project's documents…",
}: ComposerProps) {
  const fetcher = useFetcher();
  const formRef = useRef<HTMLFormElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const busy = fetcher.state !== "idle";

  // Clear and refocus once the answer arrives, so the next question can be typed immediately.
  useEffect(() => {
    if (fetcher.state === "idle" && fetcher.data) {
      formRef.current?.reset();
      if (textareaRef.current) textareaRef.current.style.height = "auto";
      textareaRef.current?.focus();
    }
  }, [fetcher.state, fetcher.data]);

  useEffect(() => {
    textareaRef.current?.focus();
  }, [conversationId]);

  return (
    <fetcher.Form method="post" ref={formRef} className="border-t bg-background/80 p-3 backdrop-blur sm:p-4">
      <input type="hidden" name="intent" value="ask" />
      <input type="hidden" name="organizationId" value={organizationId} />
      <input type="hidden" name="projectId" value={projectId} />
      {conversationId && <input type="hidden" name="conversationId" value={conversationId} />}

      <div className="mx-auto flex max-w-3xl items-end gap-2 rounded-2xl border bg-card p-2 shadow-sm focus-within:ring-2 focus-within:ring-ring/40">
        <Textarea
          ref={textareaRef}
          name="question"
          rows={1}
          required
          disabled={disabled || busy}
          placeholder={placeholder}
          className="max-h-40 min-h-9 resize-none border-0 bg-transparent px-2 py-2 text-sm shadow-none focus-visible:ring-0"
          onInput={(event) => {
            const element = event.currentTarget;
            element.style.height = "auto";
            element.style.height = `${Math.min(element.scrollHeight, 160)}px`;
          }}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              event.currentTarget.form?.requestSubmit();
            }
          }}
        />
        <Button
          aria-label="Send question"
          disabled={disabled || busy}
          size="icon"
          type="submit"
          className="mb-0.5 size-8 rounded-xl"
        >
          {busy ? <Loader2Icon className="animate-spin" /> : <ArrowUpIcon />}
        </Button>
      </div>

      <p className="mx-auto mt-2 max-w-3xl text-center text-[11px] text-muted-foreground">
        Answers come only from the documents ingested into this project. Enter sends, Shift+Enter adds a line.
      </p>
    </fetcher.Form>
  );
}
