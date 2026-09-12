import {
  FileStackIcon,
  FolderKanbanIcon,
  MessagesSquareIcon,
  PlusIcon,
  SparklesIcon,
} from "lucide-react";
import { useEffect, useRef } from "react";
import { Link, useFetcher, useLoaderData, type ActionFunctionArgs, type LoaderFunctionArgs } from "react-router";
import { toast } from "sonner";

import { EmptyState } from "~/components/empty-state";
import { StatusBadge } from "~/components/status-badge";
import { SubmitButton } from "~/components/submit-button";
import { Button } from "~/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "~/components/ui/card";
import { Input } from "~/components/ui/input";
import { Label } from "~/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "~/components/ui/table";
import { Textarea } from "~/components/ui/textarea";
import type { ActionResult } from "~/lib/api/server";
import { api, attempt, load } from "~/lib/api/server";
import type { Project } from "~/lib/api/types";
import { errorOf, failure, fieldError } from "~/lib/forms";
import { requireOrganization } from "~/lib/organization.server";

/** How many projects get their document count resolved on the dashboard. */
const COUNTED_PROJECTS = 8;

export interface ProjectSummary {
  project: Project;
  documentCount: number | null;
}

export async function loader(args: LoaderFunctionArgs) {
  const { organization } = await requireOrganization(args);

  return load(async () => {
    const client = api(args.context);
    const [projects, conversations] = await Promise.all([
      client.listProjects(organization.id, { size: 100 }),
      client.listOrganizationConversations(organization.id, { size: 1 }),
    ]);

    // Documents live under projects, so the totals come from a bounded fan-out rather than a scan.
    const counted = projects.content.slice(0, COUNTED_PROJECTS);
    const counts = await Promise.all(
      counted.map(async (project) => {
        try {
          const documents = await client.listDocuments(organization.id, project.id, { size: 1 });
          return documents.totalElements;
        } catch {
          return null;
        }
      }),
    );

    const summaries: ProjectSummary[] = projects.content.map((project) => {
      const index = counted.findIndex((candidate) => candidate.id === project.id);
      return { project, documentCount: index >= 0 ? (counts[index] ?? null) : null };
    });

    const known = summaries.map((summary) => summary.documentCount).filter((count): count is number => count !== null);

    return {
      organization,
      summaries,
      projectsTotal: projects.totalElements,
      activeProjects: projects.content.filter((project) => project.status === "ACTIVE").length,
      documentsTotal: known.length === counted.length ? known.reduce((sum, count) => sum + count, 0) : null,
      sessionsTotal: conversations.totalElements,
      countsTruncated: projects.content.length > counted.length,
    };
  });
}

export async function action(args: ActionFunctionArgs) {
  const { organization } = await requireOrganization(args);
  const form = await args.request.formData();
  const intent = String(form.get("intent") ?? "");

  if (intent === "create-project") {
    const name = String(form.get("name") ?? "").trim();
    const description = String(form.get("description") ?? "").trim();
    if (!name) {
      return failure("VALIDATION_FAILED", "Request validation failed", [
        { field: "name", message: "must not be blank" },
      ]);
    }
    return attempt(() =>
      api(args.context).createProject(organization.id, {
        name,
        description: description || undefined,
      }),
    );
  }

  return failure("INVALID_REQUEST", `Unknown intent '${intent}'`);
}

export default function Dashboard() {
  const data = useLoaderData<typeof loader>();
  const { organization } = data;
  const fetcher = useFetcher<ActionResult<Project>>();
  const formRef = useRef<HTMLFormElement>(null);

  const created = fetcher.data?.ok ? fetcher.data.data : undefined;
  useEffect(() => {
    if (created) {
      toast.success(`Project '${created.name}' created`);
      formRef.current?.reset();
    }
  }, [created]);

  const error = errorOf(fetcher.data);

  return (
    <div className="mx-auto w-full max-w-6xl">
      <header className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="font-heading text-3xl font-semibold tracking-tight">{organization.name}</h1>
            <StatusBadge status={organization.status} />
          </div>
          <p className="max-w-2xl text-sm text-muted-foreground">
            Everything here belongs to your organization. Create a project, ingest its documents, then ask
            questions that are answered only from them.
          </p>
        </div>

        <Button asChild size="sm">
          <Link to="/chat">
            <MessagesSquareIcon data-icon="inline-start" />
            Ask a question
          </Link>
        </Button>
      </header>

      <div className="mb-8 grid gap-4 sm:grid-cols-3">
        <Stat
          icon={FolderKanbanIcon}
          label="Projects"
          value={data.projectsTotal}
          hint={`${data.activeProjects} active`}
        />
        <Stat
          icon={FileStackIcon}
          label="Documents"
          value={data.documentsTotal}
          hint={data.countsTruncated ? "first projects counted" : "ingested and embedded"}
        />
        <Stat
          icon={MessagesSquareIcon}
          label="Chat sessions"
          value={data.sessionsTotal}
          hint="across every project"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <Card>
          <CardHeader>
            <CardTitle>Projects</CardTitle>
            <CardDescription>
              A project is one body of knowledge. Its documents never leak into another project's answers.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {data.summaries.length === 0 ? (
              <EmptyState
                icon={SparklesIcon}
                title="Start with a project"
                description="Then upload a PDF, markdown or text file and the chatbot can answer from it."
              />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Project</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Documents</TableHead>
                    <TableHead className="w-40 text-right">Open</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.summaries.map(({ project, documentCount }) => (
                    <TableRow key={project.id} className="group">
                      <TableCell className="max-w-0">
                        <Link to={`/projects/${project.id}`} className="block truncate font-medium hover:underline">
                          {project.name}
                        </Link>
                        {project.description && (
                          <span className="block truncate text-xs text-muted-foreground">
                            {project.description}
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={project.status} />
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {documentCount ?? <span className="text-muted-foreground">—</span>}
                      </TableCell>
                      <TableCell className="space-x-1 text-right whitespace-nowrap">
                        <Button asChild size="sm" variant="ghost">
                          <Link to={`/projects/${project.id}`}>Documents</Link>
                        </Button>
                        <Button asChild size="sm" variant="outline">
                          <Link to={`/chat?project=${project.id}`}>Chat</Link>
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>

        <Card className="h-fit">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <PlusIcon className="size-4" />
              New project
            </CardTitle>
            <CardDescription>Name it after what it knows.</CardDescription>
          </CardHeader>
          <fetcher.Form method="post" ref={formRef}>
            <CardContent className="space-y-4">
              <input type="hidden" name="intent" value="create-project" />
              <div className="space-y-2">
                <Label htmlFor="project-name">Name</Label>
                <Input id="project-name" name="name" placeholder="HR Policies" required autoComplete="off" />
                {fieldError(fetcher.data, "name") && (
                  <p className="text-xs text-destructive">{fieldError(fetcher.data, "name")}</p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="project-description">Description (optional)</Label>
                <Textarea
                  id="project-description"
                  name="description"
                  rows={3}
                  placeholder="What this project should answer about."
                />
              </div>
              {error && !error.violations.length && <p className="text-xs text-destructive">{error.message}</p>}
            </CardContent>
            <CardFooter>
              <SubmitButton pending={fetcher.state !== "idle"} className="w-full">
                <PlusIcon data-icon="inline-start" />
                Create project
              </SubmitButton>
            </CardFooter>
          </fetcher.Form>
        </Card>
      </div>
    </div>
  );
}

function Stat({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: typeof FolderKanbanIcon;
  label: string;
  value: number | null;
  hint: string;
}) {
  return (
    <Card>
      <CardContent className="flex items-start justify-between gap-4 pt-6">
        <div className="space-y-1">
          <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">{label}</p>
          <p className="font-heading text-3xl font-semibold tabular-nums">
            {value ?? <span className="text-muted-foreground">—</span>}
          </p>
          <p className="text-xs text-muted-foreground">{hint}</p>
        </div>
        <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
          <Icon className="size-4" />
        </div>
      </CardContent>
    </Card>
  );
}
