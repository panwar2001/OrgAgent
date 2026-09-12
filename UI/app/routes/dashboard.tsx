import { FolderKanbanIcon, MessagesSquareIcon, PlusIcon } from "lucide-react";
import { useEffect, useRef } from "react";
import { Link, useFetcher, useLoaderData, type ActionFunctionArgs, type LoaderFunctionArgs } from "react-router";
import { toast } from "sonner";

import { EmptyState } from "~/components/empty-state";
import { PageHeader } from "~/components/page-header";
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

/**
 * The organization's dashboard: this is the landing page after sign-in, because signing in already
 * put you inside your organization.
 */
export async function loader(args: LoaderFunctionArgs) {
  const { organization } = await requireOrganization(args);

  return load(async () => {
    const client = api(args.context);
    const projects = await client.listProjects(organization.id, { size: 100 });
    return { organization, projects };
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
  const { organization, projects } = useLoaderData<typeof loader>();
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
  const active = projects.content.filter((project) => project.status === "ACTIVE").length;

  return (
    <>
      <PageHeader
        title={organization.name}
        description="Your organization’s projects and documents."
        actions={
          <>
            <StatusBadge status={organization.status} />
            <Button asChild size="sm" variant="outline">
              <Link to="/chat">
                <MessagesSquareIcon data-icon="inline-start" />
                Open chat
              </Link>
            </Button>
          </>
        }
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <Stat label="Projects" value={projects.totalElements} hint={`${active} active`} />
        <Stat
          label="Archived"
          value={projects.content.length - active}
          hint="read-only, no new documents"
        />
        <Stat label="Chat" value={projects.content.length > 0 ? 1 : 0} hint="one workspace across projects" />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[2fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FolderKanbanIcon className="size-4" />
              Projects
            </CardTitle>
            <CardDescription>
              A project is a body of knowledge: ingest its documents, then ask questions grounded in them.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {projects.content.length === 0 ? (
              <EmptyState
                icon={FolderKanbanIcon}
                title="No projects yet"
                description="Create your first project, then upload the documents it should answer from."
              />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {projects.content.map((project) => (
                    <TableRow key={project.id}>
                      <TableCell className="font-medium">
                        {project.name}
                        {project.description && (
                          <span className="block max-w-md truncate text-xs text-muted-foreground">
                            {project.description}
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={project.status} />
                      </TableCell>
                      <TableCell className="space-x-1 text-right">
                        <Button asChild size="sm" variant="ghost">
                          <Link to={`/projects/${project.id}`}>Documents</Link>
                        </Button>
                        <Button asChild size="sm" variant="ghost">
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

        <Card className="self-start">
          <CardHeader>
            <CardTitle>New project</CardTitle>
            <CardDescription>Everything in this organization stays inside it.</CardDescription>
          </CardHeader>
          <fetcher.Form method="post" ref={formRef}>
            <CardContent className="space-y-4">
              <input type="hidden" name="intent" value="create-project" />
              <div className="space-y-2">
                <Label htmlFor="project-name">Name</Label>
                <Input id="project-name" name="name" placeholder="HR Policies" required />
                {fieldError(fetcher.data, "name") && (
                  <p className="text-xs text-destructive">{fieldError(fetcher.data, "name")}</p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="project-description">Description (optional)</Label>
                <Textarea id="project-description" name="description" rows={3} placeholder="What it answers about." />
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
    </>
  );
}

function Stat({ label, value, hint }: { label: string; value: number; hint: string }) {
  return (
    <Card>
      <CardHeader>
        <CardDescription>{label}</CardDescription>
        <CardTitle className="text-3xl">{value}</CardTitle>
      </CardHeader>
      <CardContent className="text-xs text-muted-foreground">{hint}</CardContent>
    </Card>
  );
}
