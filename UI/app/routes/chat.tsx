import {
  DatabaseIcon,
  MessageSquarePlusIcon,
  SendIcon,
  SparklesIcon,
  Trash2Icon,
  ZapIcon,
} from "lucide-react";
import { useEffect, useRef } from "react";
import {
  Link,
  redirect,
  useFetcher,
  useLoaderData,
  useLocation,
  useNavigate,
  useParams,
  type ActionFunctionArgs,
  type LoaderFunctionArgs,
} from "react-router";
import { toast } from "sonner";

import { EmptyState } from "~/components/empty-state";
import { PageHeader } from "~/components/page-header";
import { SubmitButton } from "~/components/submit-button";
import { Badge } from "~/components/ui/badge";
import { Button } from "~/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "~/components/ui/card";
import { Label } from "~/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "~/components/ui/select";
import { Separator } from "~/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "~/components/ui/tabs";
import { Textarea } from "~/components/ui/textarea";
import type { ActionResult } from "~/lib/api/server";
import { api, attempt, load } from "~/lib/api/server";
import type { ChatAnswer, ChatMessage } from "~/lib/api/types";
import { errorOf, failure, fieldError } from "~/lib/forms";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const organizationId = params.organizationId!;
  const projectId = params.projectId!;
  const conversationId = new URL(request.url).searchParams.get("conversationId");

  return load(async () => {
    const client = api(context);
    const [project, conversations] = await Promise.all([
      client.getProject(organizationId, projectId),
      client.listConversations(organizationId, projectId, { size: 50 }),
    ]);

    if (!conversationId) {
      return { project, conversations, selected: null, window: null, history: null };
    }

    const [window, history] = await Promise.all([
      client.getConversationWindow(organizationId, projectId, conversationId),
      client.getConversationHistory(organizationId, projectId, conversationId, { size: 100 }),
    ]);
    return { project, conversations, selected: conversationId, window, history };
  });
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  const organizationId = params.organizationId!;
  const projectId = params.projectId!;
  const client = api(context);
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "");

  switch (intent) {
    case "ask": {
      const question = String(form.get("question") ?? "").trim();
      const conversationId = String(form.get("conversationId") ?? "").trim();
      if (!question) {
        return failure("VALIDATION_FAILED", "Request validation failed", [
          { field: "question", message: "must not be blank" },
        ]);
      }
      return attempt(() =>
        client.ask(organizationId, projectId, {
          question,
          conversationId: conversationId || undefined,
        }),
      );
    }
    case "delete-conversation": {
      const conversationId = String(form.get("conversationId") ?? "");
      const result = await attempt(async () => {
        await client.deleteConversation(organizationId, projectId, conversationId);
        return { deleted: true };
      });
      return result.ok ? redirect(`?`) : result;
    }
    default:
      return failure("INVALID_REQUEST", `Unknown intent '${intent}'`);
  }
}

export default function Chat() {
  const { project, conversations, selected, window, history } = useLoaderData<typeof loader>();
  const params = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const fetcher = useFetcher<ActionResult<ChatAnswer>>();
  const formRef = useRef<HTMLFormElement>(null);

  const answer = fetcher.data?.ok ? fetcher.data.data : undefined;
  const asked = fetcher.formData?.get("question")?.toString();

  // A brand new conversation only gets its id from the answer, so adopt it in the URL.
  useEffect(() => {
    if (answer && !selected) {
      navigate(`${location.pathname}?conversationId=${answer.conversationId}`, { replace: true });
    }
  }, [answer, selected, navigate, location.pathname]);

  useEffect(() => {
    if (answer) formRef.current?.reset();
  }, [answer]);

  const askError = errorOf(fetcher.data);
  const messages: ChatMessage[] = history?.content ?? [];
  const liveAnswer = answer && answer.conversationId === selected ? answer : undefined;

  return (
    <>
      <PageHeader
        title={`${project.name} chat`}
        description="Questions are answered from this project's documents only, with the last turns of the conversation replayed from Redis."
        breadcrumb={[
          { label: "Organizations", to: "/organizations" },
          { label: "Project", to: `/organizations/${params.organizationId}/projects/${params.projectId}` },
          { label: "Chat" },
        ]}
        actions={
          <Button asChild size="sm" variant="outline">
            <Link to={location.pathname} reloadDocument>
              <MessageSquarePlusIcon data-icon="inline-start" />
              New conversation
            </Link>
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_18rem]">
        <Card className="flex min-h-[32rem] flex-col">
          <CardHeader>
            <CardTitle>Conversation</CardTitle>
            <CardDescription>
              {messages.length > 0
                ? `${messages.length} logged turn(s) in Postgres`
                : "Ask a question to start the conversation."}
            </CardDescription>
          </CardHeader>

          <CardContent className="flex-1 space-y-4">
            {messages.length === 0 && !liveAnswer && (
              <EmptyState
                icon={SparklesIcon}
                title="No messages yet"
                description="Ask something the project's documents should be able to answer."
              />
            )}

            {messages.map((message) => (
              <Bubble
                key={message.id}
                role={message.role}
                content={message.content}
                meta={
                  message.role === "ASSISTANT"
                    ? [
                        message.model ?? "model",
                        message.latencyMs !== null ? `${message.latencyMs} ms` : null,
                        message.servedFromCache ? "semantic cache" : null,
                      ]
                        .filter(Boolean)
                        .join(" · ")
                    : undefined
                }
              />
            ))}

            {asked && <Bubble role="USER" content={asked} pending />}

            {liveAnswer && (
              <Bubble
                role="ASSISTANT"
                content={liveAnswer.answer}
                meta={[
                  liveAnswer.model ?? "model",
                  liveAnswer.latencyMs !== null ? `${liveAnswer.latencyMs} ms` : null,
                  liveAnswer.fromCache ? "semantic cache" : null,
                ]
                  .filter(Boolean)
                  .join(" · ")}
                sources={liveAnswer.sources}
              />
            )}

            {askError && (
              <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm">
                <p className="font-medium text-destructive">
                  {askError.code === "BACKEND_UNREACHABLE" ? "Backend not reachable" : "Answer failed"}
                </p>
                <p className="text-muted-foreground">{askError.message}</p>
              </div>
            )}
          </CardContent>

          <Separator />

          <fetcher.Form method="post" ref={formRef} className="space-y-3 p-6">
            <input type="hidden" name="intent" value="ask" />
            {selected && <input type="hidden" name="conversationId" value={selected} />}
            <div className="space-y-2">
              <Label htmlFor="question">Question</Label>
              <Textarea
                id="question"
                name="question"
                rows={3}
                placeholder="How long do refunds take?"
                required
                onKeyDown={(event) => {
                  if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
                    event.currentTarget.form?.requestSubmit();
                  }
                }}
              />
              {fieldError(fetcher.data, "question") && (
                <p className="text-xs text-destructive">{fieldError(fetcher.data, "question")}</p>
              )}
            </div>
            <div className="flex items-center justify-between gap-3">
              <p className="text-xs text-muted-foreground">⌘/Ctrl + Enter to send</p>
              <SubmitButton pending={fetcher.state !== "idle"}>
                <SendIcon data-icon="inline-start" />
                Ask
              </SubmitButton>
            </div>
          </fetcher.Form>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Conversations</CardTitle>
              <CardDescription>Pick up where a conversation left off.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {conversations.content.length === 0 ? (
                <p className="text-sm text-muted-foreground">No conversations yet.</p>
              ) : (
                <Select
                  value={selected ?? undefined}
                  onValueChange={(conversationId) => navigate(`?conversationId=${conversationId}`)}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select a conversation" />
                  </SelectTrigger>
                  <SelectContent>
                    {conversations.content.map((conversation) => (
                      <SelectItem key={conversation.id} value={conversation.id}>
                        {conversation.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}

              {selected && <DeleteConversation conversationId={selected} />}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Where the data lives</CardTitle>
              <CardDescription>Redis serves the live window; Postgres is the system of record.</CardDescription>
            </CardHeader>
            <CardContent>
              <Tabs defaultValue="window">
                <TabsList className="w-full">
                  <TabsTrigger value="window">
                    <ZapIcon data-icon="inline-start" />
                    Redis
                  </TabsTrigger>
                  <TabsTrigger value="log">
                    <DatabaseIcon data-icon="inline-start" />
                    Postgres
                  </TabsTrigger>
                </TabsList>
                <TabsContent value="window" className="space-y-2 pt-3">
                  {window?.turns.length ? (
                    window.turns
                      .slice()
                      .reverse()
                      .map((turn, index) => (
                        <div key={index} className="rounded-md border p-2 text-xs">
                          <span className="font-medium">{turn.role}</span>
                          <p className="line-clamp-3 text-muted-foreground">{turn.content}</p>
                        </div>
                      ))
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      {selected ? "The live window is empty (or has expired)." : "Select a conversation."}
                    </p>
                  )}
                </TabsContent>
                <TabsContent value="log" className="space-y-2 pt-3">
                  {messages.length ? (
                    messages
                      .slice(-10)
                      .reverse()
                      .map((message) => (
                        <div key={message.id} className="rounded-md border p-2 text-xs">
                          <span className="font-medium">{message.role}</span>
                          <p className="line-clamp-3 text-muted-foreground">{message.content}</p>
                        </div>
                      ))
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      {selected ? "Nothing logged yet." : "Select a conversation."}
                    </p>
                  )}
                </TabsContent>
              </Tabs>
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}

function Bubble({
  role,
  content,
  meta,
  sources,
  pending,
}: {
  role: "USER" | "ASSISTANT" | "SYSTEM";
  content: string;
  meta?: string;
  sources?: ChatAnswer["sources"];
  pending?: boolean;
}) {
  const isUser = role === "USER";

  return (
    <div className={isUser ? "flex justify-end" : "flex justify-start"}>
      <div
        className={
          isUser
            ? "max-w-[85%] rounded-lg bg-primary px-4 py-2 text-primary-foreground"
            : "max-w-[85%] space-y-2 rounded-lg border bg-muted/40 px-4 py-2"
        }
      >
        <p className={pending ? "whitespace-pre-wrap opacity-60" : "whitespace-pre-wrap"}>{content}</p>

        {meta && <p className="text-xs opacity-70">{meta}</p>}

        {sources && sources.length > 0 && (
          <div className="flex flex-wrap gap-1 pt-1">
            {sources.map((source, index) => (
              <Badge key={`${source.documentId ?? source.title}-${index}`} variant="outline">
                {source.title}
                {source.score !== null ? ` · ${source.score.toFixed(2)}` : ""}
              </Badge>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function DeleteConversation({ conversationId }: { conversationId: string }) {
  const fetcher = useFetcher<ActionResult<{ deleted: boolean }>>();

  const deleted = fetcher.data?.ok;
  useEffect(() => {
    if (deleted) toast.success("Conversation deleted");
  }, [deleted]);

  return (
    <fetcher.Form
      method="post"
      onSubmit={(event) => {
        if (!confirm("Delete this conversation, its live window and its log?")) event.preventDefault();
      }}
    >
      <input type="hidden" name="intent" value="delete-conversation" />
      <input type="hidden" name="conversationId" value={conversationId} />
      <SubmitButton pending={fetcher.state !== "idle"} size="sm" variant="outline" className="w-full">
        <Trash2Icon data-icon="inline-start" />
        Delete conversation
      </SubmitButton>
    </fetcher.Form>
  );
}
