import { MessageSquareIcon, PlusIcon, SearchIcon, Trash2Icon } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useFetcher } from "react-router";

import { Button } from "~/components/ui/button";
import { Input } from "~/components/ui/input";
import { ScrollArea } from "~/components/ui/scroll-area";
import type { ConversationSummary } from "~/lib/api/types";
import { cn } from "~/lib/utils";

interface SessionRailProps {
  organizationId: string;
  organizationName: string;
  sessions: ConversationSummary[];
  activeId?: string;
  projectId?: string;
}

const BUCKETS = ["Today", "Yesterday", "Previous 7 days", "Previous 30 days", "Older"] as const;

/** Which date bucket a session belongs in, ChatGPT style. */
function bucketOf(iso: string): (typeof BUCKETS)[number] {
  const updated = new Date(iso);
  const midnight = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const days = Math.round((midnight(new Date()) - midnight(updated)) / 86_400_000);
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days <= 7) return "Previous 7 days";
  if (days <= 30) return "Previous 30 days";
  return "Older";
}

function relativeTime(iso: string): string {
  const diffMinutes = Math.round((Date.now() - new Date(iso).getTime()) / 60_000);
  if (diffMinutes < 1) return "just now";
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const hours = Math.round(diffMinutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(iso).toLocaleDateString();
}

/**
 * The left rail: every session of the organization, newest first, grouped by date and filterable.
 * Each entry links to `?c=<conversationId>&project=<projectId>`, so a session is a shareable URL.
 */
export function SessionRail({ organizationId, organizationName, sessions, activeId, projectId }: SessionRailProps) {
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return sessions;
    return sessions.filter(
      (session) =>
        session.title.toLowerCase().includes(needle) || session.projectName.toLowerCase().includes(needle),
    );
  }, [sessions, query]);

  const grouped = useMemo(() => {
    return BUCKETS.map((bucket) => ({
      bucket,
      items: filtered.filter((session) => bucketOf(session.updatedAt) === bucket),
    })).filter((group) => group.items.length > 0);
  }, [filtered]);

  const newChatHref = `/chat${projectId ? `?project=${projectId}` : ""}`;

  return (
    <div className="flex h-full min-h-0 flex-col border-r bg-sidebar text-sidebar-foreground">
      <div className="space-y-3 border-b p-3">
        <Button asChild className="w-full justify-start" size="sm">
          <Link to={newChatHref}>
            <PlusIcon data-icon="inline-start" />
            New chat
          </Link>
        </Button>

        <div className="relative">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Search sessions"
            className="h-8 pl-8 text-sm"
            placeholder="Search sessions"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
      </div>

      <ScrollArea className="min-h-0 flex-1">
        <nav className="space-y-4 p-2" aria-label="Chat sessions">
          {grouped.length === 0 && (
            <p className="px-2 py-6 text-center text-xs text-muted-foreground">
              {sessions.length === 0 ? "No sessions yet." : "No session matches that."}
            </p>
          )}

          {grouped.map((group) => (
            <div key={group.bucket} className="space-y-1">
              <p className="px-2 text-[11px] font-medium tracking-wide text-muted-foreground uppercase">
                {group.bucket}
              </p>
              {group.items.map((session) => (
                <SessionItem
                  key={session.id}
                  session={session}
                  active={session.id === activeId}
                  organizationId={organizationId}
                />
              ))}
            </div>
          ))}
        </nav>
      </ScrollArea>

      <div className="border-t p-3 text-[11px] text-muted-foreground">
        <p className="truncate font-medium text-foreground">{organizationName}</p>
        <p>{sessions.length} session(s) in this organization</p>
      </div>
    </div>
  );
}

function SessionItem({
  session,
  active,
  organizationId,
}: {
  session: ConversationSummary;
  active: boolean;
  organizationId: string;
}) {
  const fetcher = useFetcher();

  return (
    <div
      className={cn(
        "group/session relative flex items-center rounded-md",
        active ? "bg-sidebar-accent text-sidebar-accent-foreground" : "hover:bg-sidebar-accent/60",
      )}
    >
      <Link
        to={`/chat?c=${session.id}&project=${session.projectId}`}
        className="min-w-0 flex-1 px-2 py-2"
      >
        <span className="flex items-center gap-2">
          <MessageSquareIcon className="size-3.5 shrink-0 opacity-70" />
          <span className="truncate text-sm">{session.title}</span>
        </span>
        <span className="mt-0.5 flex items-center gap-1.5 pl-5.5 text-[11px] text-muted-foreground">
          <span className="truncate">{session.projectName}</span>
          <span aria-hidden>·</span>
          <span className="shrink-0">{relativeTime(session.updatedAt)}</span>
        </span>
      </Link>

      <fetcher.Form
        method="post"
        className="pr-1 opacity-0 transition-opacity group-hover/session:opacity-100 focus-within:opacity-100"
        onSubmit={(event) => {
          if (!confirm(`Delete session '${session.title}'?`)) event.preventDefault();
        }}
      >
        <input type="hidden" name="intent" value="delete-conversation" />
        <input type="hidden" name="conversationId" value={session.id} />
        <input type="hidden" name="projectId" value={session.projectId} />
        {active && <input type="hidden" name="clearActive" value="true" />}
        <Button
          aria-label={`Delete ${session.title}`}
          disabled={fetcher.state !== "idle"}
          size="icon"
          type="submit"
          variant="ghost"
          className="size-7"
        >
          <Trash2Icon className="size-3.5" />
        </Button>
      </fetcher.Form>
    </div>
  );
}
