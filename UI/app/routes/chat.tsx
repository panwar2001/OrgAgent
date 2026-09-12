import { BookOpenIcon, MessageSquareIcon, SparklesIcon } from "lucide-react";
import { useEffect, useRef } from "react";
import {
  Link,
  redirect,
  useFetcher,
  useLoaderData,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
  type ActionFunctionArgs,
  type LoaderFunctionArgs,
} from "react-router";

import { Composer } from "~/components/chat/composer";
import { MessageBubble } from "~/components/chat/message-bubble";
import { SessionRail } from "~/components/chat/session-rail";
import { PageHeader } from "~/components/page-header";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Badge } from "~/components/ui/badge";
import { Button } from "~/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "~/components/ui/card";
import { Label } from "~/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "~/components/ui/select";
import type { ActionResult } from "~/lib/api/server";
import { api, attempt, load } from "~/lib/api/server";
import type { ChatAnswer, ChatMessage, ConversationSummary, Project } from "~/lib/api/types";
import { errorOf, failure, fieldError } from "~/lib/forms";

const SUGGESTIONS = [
  "Summarise what this project's documents cover.",
  "What are the main rules or policies described?",
  "Which exceptions or edge cases do the documents mention?",
];

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const organizationId = params.organizationId!;
  const url = new URL(request.url);
  const conversationId = url.searchParams.get("c");
  const projectFromUrl = url.searchParams.get("project");

  return load(async () => {
    const client = api(context);
    const [organization, sessions, projects] = await Promise.all([
      client.getOrganization(organizationId),
      client.listOrganizationConversations(organizationId, { size: 60 }),
      client.listProjects(organizationId, { size: 100 }),
    ]);

    const selectedProjectId = projectFromUrl ?? undefined;

    if (!conversationId || !selectedProjectId) {
      return {
        organization,
        sessions,
        projects,
        activeId: null,
        project: null,
        history: [] as ChatMessage[],
        liveTurns: [] as { role: string; content: string; at: string }[],
        projectId: selectedProjectId,
      };
    }

    const [history, window, project] = await Promise.all([
      // 100 is the API's maximum page size, so a session shows its most recent 100 turns.
      client.getConversationHistory(organizationId, selectedProjectId, conversationId, { size: 100 }),
      client.getConversationWindow(organizationId, selectedProjectId, conversationId),
      client.getProject(organizationId, selectedProjectId),
    ]);

    return {
      organization,
      sessions,
      projects,
      activeId: conversationId,
      project,
      history: history.content,
      liveTurns: window.turns,
      projectId: selectedProjectId,
    };
  });
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  const organizationId = params.organizationId!;
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "");
  const client = api(context);

  if (intent === "ask") {
    const question = String(form.get("question") ?? "").trim();
    const projectId = String(form.get("projectId") ?? "").trim();
    const conversationId = String(form.get("conversationId") ?? "").trim();

    if (!projectId) {
      return failure("VALIDATION_FAILED", "Choose a project first", [
        { field: "projectId", message: "a project is required to answer from documents" },
      ]);
    }
    if (!question) {
      return failure("VALIDATION_FAILED", "Request validation failed", [
        { field: "question", message: "must not be blank" },
      ]);
    }

    const result = await attempt(() =>
      client.ask(organizationId, projectId, { question, conversationId: conversationId || undefined }),
    );

    // A brand new conversation only gets its id from the answer: adopt it so the session is real.
    if (result.ok && !conversationId) {
      return redirect(`/organizations/${organizationId}/chat?c=${result.data.conversationId}&project=${projectId}`);
    }
    return result;
  }

  if (intent === "delete-conversation") {
    const conversationId = String(form.get("conversationId") ?? "");
    const projectId = String(form.get("projectId") ?? "");
    const result = await attempt(async () => {
      await client.deleteConversation(organizationId, projectId, conversationId);
      return { deleted: true };
    });
    return result.ok ? redirect(`/organizations/${organizationId}/chat`) : result;
  }

  return failure("INVALID_REQUEST", `Unknown intent '${intent}'`);
}

export default function Chat() {
  const data = useLoaderData<typeof loader>();
  const params = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const fetcher = useFetcher<ActionResult<ChatAnswer>>();
  const bottomRef = useRef<HTMLDivElement>(null);
  const composerAnchorRef = useRef<HTMLDivElement>(null);

  const answer = fetcher.data?.ok ? fetcher.data.data : undefined;
  const askError = errorOf(fetcher.data);
  const pendingQuestion = fetcher.formData?.get("question")?.toString();
  const pendingProjectId = fetcher.formData?.get("projectId")?.toString();

  const messages = data.history;
  const answeredInHistory =
    answer && messages.some((message) => message.role === "ASSISTANT" && message.content === answer.answer);
  const questionInHistory =
    pendingQuestion && messages.some((message) => message.role === "USER" && message.content === pendingQuestion);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages.length, answer, fetcher.state]);

  const projectId = data.projectId;
  const activeProject = data.project;
  const selectedProjectId = projectId ?? data.projects.content[0]?.id;

  return (
    <div className="-m-4 flex h-[calc(100svh-0px)] min-h-0 lg:-m-8">
      <aside className="hidden w-72 shrink-0 lg:block">
        <SessionRail
          organizationId={data.organization.id}
          organizationName={data.organization.name}
          sessions={data.sessions.content}
          activeId={data.activeId ?? undefined}
          projectId={selectedProjectId}
        />
      </aside>

      <section className="flex min-h-0 min-w-0 flex-1 flex-col">
        <div className="flex items-center justify-between gap-3 border-b px-4 py-3 lg:px-6">
          <div className="min-w-0">
            <p className="truncate font-heading text-sm font-semibold">
              {activeProject ? activeProject.name : "New chat"}
            </p>
            <p className="truncate text-xs text-muted-foreground">
              {data.organization.name} · {data.sessions.totalElements} session(s)
              {activeProject ? ` · ${activeProject.slug}` : ""}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button asChild size="sm" variant="outline" className="lg:hidden">
              <Link to={`/organizations/${data.organization.id}`}>
                <MessageSquareIcon data-icon="inline-start" />
                Sessions
              </Link>
            </Button>
            <Button asChild size="sm" variant="ghost">
              <Link to={`/organizations/${data.organization.id}/chat`}>New chat</Link>
            </Button>
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto">
          <div className="mx-auto flex max-w-3xl flex-col gap-6 px-4 py-6 lg:px-6">
            {messages.length === 0 && !answer && (
              <NewChatPanel
                organizationId={data.organization.id}
                projects={data.projects.content}
                selectedProjectId={selectedProjectId}
                onSelectProject={(value) => {
                  const next = new URLSearchParams(searchParams);
                  next.set("project", value);
                  setSearchParams(next, { replace: true });
                }}
                locationKey={location.key}
              />
            )}

            {messages.map((message) => (
              <MessageBubble
                key={message.id}
                role={message.role}
                content={message.content}
                model={message.model}
                latencyMs={message.latencyMs}
                fromCache={message.servedFromCache}
              />
            ))}

            {pendingQuestion && !questionInHistory && <MessageBubble role="USER" content={pendingQuestion} />}
            {pendingQuestion && !answer && <MessageBubble role="ASSISTANT" content="" pending />}

            {answer && !answeredInHistory && (
              <MessageBubble
                role="ASSISTANT"
                content={answer.answer}
                model={answer.model}
                latencyMs={answer.latencyMs}
                fromCache={answer.fromCache}
                sources={answer.sources}
              />
            )}

            {askError && (
              <Alert variant="destructive">
                <AlertTitle>
                  {askError.code === "BACKEND_UNREACHABLE" ? "Backend not reachable" : "Answer failed"} (
                  {askError.code})
                </AlertTitle>
                <AlertDescription>
                  <p>{askError.message}</p>
                  {fieldError(fetcher.data, "projectId") && <p>{fieldError(fetcher.data, "projectId")}</p>}
                </AlertDescription>
              </Alert>
            )}

            <div ref={bottomRef} />
          </div>
        </div>

        <div ref={composerAnchorRef}>
          {selectedProjectId ? (
            <Composer
              organizationId={data.organization.id}
              projectId={selectedProjectId}
              conversationId={data.activeId ?? undefined}
            />
          ) : (
            <div className="border-t p-4 text-center text-sm text-muted-foreground">
              Create a project with documents before asking questions.
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function NewChatPanel({
  organizationId,
  projects,
  selectedProjectId,
  onSelectProject,
  locationKey,
}: {
  organizationId: string;
  projects: Project[];
  selectedProjectId?: string;
  onSelectProject: (value: string) => void;
  locationKey: string;
}) {
  const fetcher = useFetcher<ActionResult<ChatAnswer>>();

  if (projects.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>No projects yet</CardTitle>
          <CardDescription>
            A chat answers from one project's documents, so create a project and ingest a file first.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild size="sm">
            <Link to={`/organizations/${organizationId}`}>Go to projects</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6 py-6">
      <div className="space-y-2 text-center">
        <div className="mx-auto flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <SparklesIcon className="size-5" />
        </div>
        <h2 className="font-heading text-xl font-semibold">Ask this project's documents</h2>
        <p className="mx-auto max-w-md text-sm text-muted-foreground">
          Answers are grounded in ingested files, cited, and never invented. Pick the project to ask about.
        </p>
      </div>

      <div className="mx-auto w-full max-w-sm space-y-2">
        <Label htmlFor="project-picker">Project</Label>
        <Select value={selectedProjectId} onValueChange={onSelectProject}>
          <SelectTrigger id="project-picker" className="w-full">
            <SelectValue placeholder="Choose a project" />
          </SelectTrigger>
          <SelectContent>
            {projects.map((project) => (
              <SelectItem key={project.id} value={project.id}>
                {project.name}
                {project.status === "ARCHIVED" ? " (archived)" : ""}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <fetcher.Form method="post" className="mx-auto grid w-full max-w-2xl gap-2 sm:grid-cols-3">
        <input type="hidden" name="intent" value="ask" />
        <input type="hidden" name="projectId" value={selectedProjectId ?? ""} />
        {SUGGESTIONS.map((suggestion) => (
          <Button
            key={`${locationKey}-${suggestion}`}
            type="submit"
            name="question"
            value={suggestion}
            variant="outline"
            disabled={!selectedProjectId || fetcher.state !== "idle"}
            className="h-auto justify-start px-3 py-2.5 text-left text-xs font-normal whitespace-normal"
          >
            <BookOpenIcon data-icon="inline-start" />
            {suggestion}
          </Button>
        ))}
      </fetcher.Form>

      {errorOf(fetcher.data) && (
        <Alert variant="destructive">
          <AlertTitle>{errorOf(fetcher.data)?.code}</AlertTitle>
          <AlertDescription>{errorOf(fetcher.data)?.message}</AlertDescription>
        </Alert>
      )}

      <div className="flex items-center justify-center gap-2 text-xs text-muted-foreground">
        <Badge variant="outline">knowledge base: the selected project</Badge>
        <Badge variant="outline">citations included</Badge>
      </div>
    </div>
  );
}
