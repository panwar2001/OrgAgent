import { FolderKanbanIcon, PlusIcon, PowerIcon, Trash2Icon } from "lucide-react";
import { useEffect, useRef } from "react";
import {
  Link,
  redirect,
  useFetcher,
  useLoaderData,
  useParams,
  type ActionFunctionArgs,
  type LoaderFunctionArgs,
} from "react-router";
import { toast } from "sonner";

import { EmptyState } from "~/components/empty-state";
import { PageHeader } from "~/components/page-header";
import { StatusBadge } from "~/components/status-badge";
import { SubmitButton } from "~/components/submit-button";
import { Button } from "~/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import { Input } from "~/components/ui/input";
import { Label } from "~/components/ui/label";
import { Separator } from "~/components/ui/separator";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "~/components/ui/table";
import { Textarea } from "~/components/ui/textarea";
import type { ActionResult } from "~/lib/api/server";
import { api, attempt, load } from "~/lib/api/server";
import type { Project } from "~/lib/api/types";
import { errorOf, failure, fieldError } from "~/lib/forms";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const organizationId = params.organizationId!;
  const url = new URL(request.url);
  const page = Number(url.searchParams.get("page") ?? "0");

  return load(async () => {
    const client = api(context);
    const [organization, projects] = await Promise.all([
      client.getOrganization(organizationId),
      client.listProjects(organizationId, { page, size: 20 }),
    ]);
    return { organization, projects };
  });
}

export async function action({ context, request, params }: ActionFunctionArgs) {
  const organizationId = params.organizationId!;
  const client = api(context);
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "");

  switch (intent) {
    case "rename": {
      const name = String(form.get("name") ?? "").trim();
      if (!name) {
        return failure("VALIDATION_FAILED", "Request validation failed", [
          { field: "name", message: "must not be blank" },
        ]);
      }
      return attempt(() => client.renameOrganization(organizationId, name));
    }
    case "suspend":
      return attempt(() => client.suspendOrganization(organizationId));
    case "activate":
      return attempt(() => client.activateOrganization(organizationId));
    case "create-project": {
      const name = String(form.get("name") ?? "").trim();
      const slug = String(form.get("slug") ?? "").trim();
      const description = String(form.get("description") ?? "").trim();
      if (!name) {
        return failure("VALIDATION_FAILED", "Request validation failed", [
          { field: "name", message: "must not be blank" },
        ]);
      }
      return attempt(() =>
        client.createProject(organizationId, {
          name,
          slug: slug || undefined,
          description: description || undefined,
        }),
      );
    }
    case "delete-organization": {
      const result = await attempt(async () => {
        await client.deleteOrganization(organizationId);
        return { deleted: true };
      });
      return result.ok ? redirect("/organizations") : result;
    }
    default:
      return failure("INVALID_REQUEST", `Unknown intent '${intent}'`);
  }
}

export default function OrganizationDetail() {
  const { organization, projects } = useLoaderData<typeof loader>();
  const params = useParams();
  const renameFetcher = useFetcher<ActionResult<unknown>>();
  const statusFetcher = useFetcher<ActionResult<unknown>>();
  const projectFetcher = useFetcher<ActionResult<Project>>();
  const projectFormRef = useRef<HTMLFormElement>(null);

  const renamed = renameFetcher.data?.ok ? (renameFetcher.data.data as { name: string }) : undefined;
  useEffect(() => {
    if (renamed) toast.success(`Renamed to '${renamed.name}'`);
  }, [renamed]);

  const statusChanged = statusFetcher.data?.ok ? (statusFetcher.data.data as { status: string }) : undefined;
  useEffect(() => {
    if (statusChanged) toast.success(`Organization is now ${statusChanged.status.toLowerCase()}`);
  }, [statusChanged]);

  const createdProject = projectFetcher.data?.ok ? projectFetcher.data.data : undefined;
  useEffect(() => {
    if (createdProject) {
      toast.success(`Project '${createdProject.name}' created`);
      projectFormRef.current?.reset();
    }
  }, [createdProject]);

  const renameError = errorOf(renameFetcher.data);
  const projectError = errorOf(projectFetcher.data);

  return (
    <>
      <PageHeader
        title={organization.name}
        description={`Organization · ${organization.slug}`}
        breadcrumb={[{ label: "Organizations", to: "/organizations" }, { label: organization.name }]}
        actions={
          <>
            <StatusBadge status={organization.status} />
            <statusFetcher.Form method="post">
              <input
                type="hidden"
                name="intent"
                value={organization.status === "ACTIVE" ? "suspend" : "activate"}
              />
              <SubmitButton
                pending={statusFetcher.state !== "idle"}
                size="sm"
                variant={organization.status === "ACTIVE" ? "outline" : "default"}
              >
                <PowerIcon data-icon="inline-start" />
                {organization.status === "ACTIVE" ? "Suspend" : "Activate"}
              </SubmitButton>
            </statusFetcher.Form>
          </>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FolderKanbanIcon className="size-4" />
              Projects
            </CardTitle>
            <CardDescription>
              Every project has its own documents, embeddings and conversations.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {projects.content.length === 0 ? (
              <EmptyState
                icon={FolderKanbanIcon}
                title="No projects yet"
                description="Create the first project for this organization."
              />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Slug</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {projects.content.map((project) => (
                    <TableRow key={project.id}>
                      <TableCell className="font-medium">{project.name}</TableCell>
                      <TableCell className="text-muted-foreground">{project.slug}</TableCell>
                      <TableCell>
                        <StatusBadge status={project.status} />
                      </TableCell>
                      <TableCell className="space-x-1 text-right">
                        <Button asChild size="sm" variant="ghost">
                          <Link
                            to={`/organizations/${params.organizationId}/projects/${project.id}`}
                          >
                            Documents
                          </Link>
                        </Button>
                        <Button asChild size="sm" variant="ghost">
                          <Link to={`/organizations/${params.organizationId}/chat?project=${project.id}`}>Chat</Link>
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>New project</CardTitle>
              <CardDescription>Projects group the documents a chatbot may answer from.</CardDescription>
            </CardHeader>
            <projectFetcher.Form method="post" ref={projectFormRef}>
              <CardContent className="space-y-4">
                <input type="hidden" name="intent" value="create-project" />
                <div className="space-y-2">
                  <Label htmlFor="project-name">Name</Label>
                  <Input id="project-name" name="name" placeholder="HR Policies" required />
                  {fieldError(projectFetcher.data, "name") && (
                    <p className="text-xs text-destructive">{fieldError(projectFetcher.data, "name")}</p>
                  )}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="project-description">Description (optional)</Label>
                  <Textarea
                    id="project-description"
                    name="description"
                    placeholder="Everything employees ask about HR."
                    rows={3}
                  />
                </div>
                {projectError && !projectError.violations.length && (
                  <p className="text-xs text-destructive">{projectError.message}</p>
                )}
              </CardContent>
              <CardFooter>
                <SubmitButton pending={projectFetcher.state !== "idle"} className="w-full">
                  <PlusIcon data-icon="inline-start" />
                  Create project
                </SubmitButton>
              </CardFooter>
            </projectFetcher.Form>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Settings</CardTitle>
              <CardDescription>Slugs are immutable: links and stored references depend on them.</CardDescription>
            </CardHeader>
            <renameFetcher.Form method="post">
              <CardContent className="space-y-4">
                <input type="hidden" name="intent" value="rename" />
                <div className="space-y-2">
                  <Label htmlFor="organization-name">Name</Label>
                  <Input id="organization-name" name="name" defaultValue={organization.name} required />
                  {fieldError(renameFetcher.data, "name") && (
                    <p className="text-xs text-destructive">{fieldError(renameFetcher.data, "name")}</p>
                  )}
                </div>
              </CardContent>
              <CardFooter>
                <SubmitButton pending={renameFetcher.state !== "idle"} variant="outline" className="w-full">
                  Save name
                </SubmitButton>
              </CardFooter>
            </renameFetcher.Form>
            <Separator />
            <CardContent className="pt-6">
              <DeleteOrganization organizationId={organization.id} name={organization.name} />
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}

function DeleteOrganization({ organizationId, name }: { organizationId: string; name: string }) {
  const fetcher = useFetcher<ActionResult<{ deleted: boolean }>>();

  return (
    <fetcher.Form
      method="post"
      className="space-y-3"
      onSubmit={(event) => {
        if (!confirm(`Delete '${name}' and everything it owns? This cannot be undone.`)) {
          event.preventDefault();
        }
      }}
    >
      <p className="text-sm text-muted-foreground">
        Deleting an organization removes its projects, documents and conversations.
      </p>
      <input type="hidden" name="intent" value="delete-organization" />
      <input type="hidden" name="organizationId" value={organizationId} />
      <SubmitButton pending={fetcher.state !== "idle"} size="sm" variant="destructive" className="w-full">
        <Trash2Icon data-icon="inline-start" />
        Delete organization
      </SubmitButton>
      {errorOf(fetcher.data) && (
        <p className="text-xs text-destructive">{errorOf(fetcher.data)?.message}</p>
      )}
    </fetcher.Form>
  );
}
