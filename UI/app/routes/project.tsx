import {
  ArchiveIcon,
  FileTextIcon,
  MessageSquareIcon,
  PowerIcon,
  Trash2Icon,
  UploadIcon,
} from "lucide-react";
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
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
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
import type { DocumentDto } from "~/lib/api/types";
import { errorOf, fieldError } from "~/lib/forms";
import { requireOrganization } from "~/lib/organization.server";

export async function loader(args: LoaderFunctionArgs) {
  const { context, params, request } = args;
  const { organization } = await requireOrganization(args);
  const organizationId = organization.id;
  const projectId = params.projectId!;
  const url = new URL(request.url);
  const page = Number(url.searchParams.get("page") ?? "0");

  return load(async () => {
    const client = api(context);
    const [project, documents] = await Promise.all([
      client.getProject(organizationId, projectId),
      client.listDocuments(organizationId, projectId, { page, size: 20 }),
    ]);
    return { project, documents };
  });
}

export async function action(args: ActionFunctionArgs) {
  const { context, params, request } = args;
  const { organization } = await requireOrganization(args);
  const organizationId = organization.id;
  const projectId = params.projectId!;
  const client = api(context);
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "");

  switch (intent) {
    case "upload": {
      const file = form.get("file");
      if (!(file instanceof File) || file.size === 0) {
        return {
          ok: false as const,
          error: {
            status: 400,
            code: "VALIDATION_FAILED",
            message: "Choose a file to ingest",
            violations: [{ field: "file", message: "a file is required" }],
          },
        };
      }
      return attempt(() => client.uploadDocument(organizationId, projectId, file));
    }
    case "delete-document": {
      const documentId = String(form.get("documentId") ?? "");
      return attempt(async () => {
        await client.deleteDocument(organizationId, projectId, documentId);
        return { documentId };
      });
    }
    case "update-project": {
      const name = String(form.get("name") ?? "").trim();
      const description = String(form.get("description") ?? "").trim();
      return attempt(() =>
        client.updateProject(organizationId, projectId, {
          name: name || undefined,
          description,
        }),
      );
    }
    case "archive":
      return attempt(() => client.archiveProject(organizationId, projectId));
    case "activate":
      return attempt(() => client.activateProject(organizationId, projectId));
    case "delete-project": {
      const result = await attempt(async () => {
        await client.deleteProject(organizationId, projectId);
        return { deleted: true };
      });
      return result.ok ? redirect("/") : result;
    }
    default:
      return {
        ok: false as const,
        error: {
          status: 400,
          code: "INVALID_REQUEST",
          message: `Unknown intent '${intent}'`,
          violations: [],
        },
      };
  }
}

export default function ProjectDetail() {
  const { project, documents } = useLoaderData<typeof loader>();
  const params = useParams();
  const uploadFetcher = useFetcher<ActionResult<DocumentDto>>();
  const settingsFetcher = useFetcher<ActionResult<unknown>>();
  const statusFetcher = useFetcher<ActionResult<unknown>>();
  const uploadFormRef = useRef<HTMLFormElement>(null);

  const uploaded = uploadFetcher.data?.ok ? uploadFetcher.data.data : undefined;
  useEffect(() => {
    if (uploaded) {
      toast.success(`Indexed '${uploaded.fileName}' into ${uploaded.chunkCount} chunk(s)`);
      uploadFormRef.current?.reset();
    }
  }, [uploaded]);

  const saved = settingsFetcher.data?.ok;
  useEffect(() => {
    if (saved) toast.success("Project updated");
  }, [saved]);

  const statusChanged = statusFetcher.data?.ok ? (statusFetcher.data.data as { status: string }) : undefined;
  useEffect(() => {
    if (statusChanged) toast.success(`Project is now ${statusChanged.status.toLowerCase()}`);
  }, [statusChanged]);

  const uploadError = errorOf(uploadFetcher.data);
  const settingsError = errorOf(settingsFetcher.data);

  return (
    <>
      <PageHeader
        title={project.name}
        description={project.description ?? "Project · documents are embedded into pgvector and answered from."}
        breadcrumb={[{ label: "Dashboard", to: "/" }, { label: project.name }]}
        actions={
          <>
            <StatusBadge status={project.status} />
            <Button asChild size="sm">
              <Link to={`/chat?project=${project.id}`}>
                <MessageSquareIcon data-icon="inline-start" />
                Open chat
              </Link>
            </Button>
            <statusFetcher.Form method="post">
              <input type="hidden" name="intent" value={project.status === "ACTIVE" ? "archive" : "activate"} />
              <SubmitButton
                pending={statusFetcher.state !== "idle"}
                size="sm"
                variant={project.status === "ACTIVE" ? "outline" : "default"}
              >
                <PowerIcon data-icon="inline-start" />
                {project.status === "ACTIVE" ? "Archive" : "Activate"}
              </SubmitButton>
            </statusFetcher.Form>
          </>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <UploadIcon className="size-4" />
                Ingest a document
              </CardTitle>
              <CardDescription>
                PDF, markdown or plain text. The file is extracted, chunked, embedded and stored in pgvector.
              </CardDescription>
            </CardHeader>
            <uploadFetcher.Form method="post" encType="multipart/form-data" ref={uploadFormRef}>
              <CardContent className="space-y-4">
                <input type="hidden" name="intent" value="upload" />
                <div className="space-y-2">
                  <Label htmlFor="file">File</Label>
                  <Input id="file" name="file" type="file" accept=".pdf,.md,.markdown,.txt,.csv,.json" required />
                  {fieldError(uploadFetcher.data, "file") && (
                    <p className="text-xs text-destructive">{fieldError(uploadFetcher.data, "file")}</p>
                  )}
                </div>
                {uploadError && !uploadError.violations.length && (
                  <Alert variant="destructive">
                    <AlertTitle>Ingestion failed ({uploadError.code})</AlertTitle>
                    <AlertDescription>{uploadError.message}</AlertDescription>
                  </Alert>
                )}
              </CardContent>
              <CardFooter>
                <SubmitButton pending={uploadFetcher.state !== "idle"} className="w-full">
                  <UploadIcon data-icon="inline-start" />
                  Upload and embed
                </SubmitButton>
              </CardFooter>
            </uploadFetcher.Form>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <FileTextIcon className="size-4" />
                Documents
              </CardTitle>
              <CardDescription>
                {documents.totalElements} document(s) · only <span className="font-medium">indexed</span> ones
                are searchable
              </CardDescription>
            </CardHeader>
            <CardContent>
              {documents.content.length === 0 ? (
                <EmptyState
                  icon={FileTextIcon}
                  title="Nothing ingested yet"
                  description="Upload the first document above; the chatbot can only answer from what it has read."
                />
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Title</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Chunks</TableHead>
                      <TableHead>Failure</TableHead>
                      <TableHead className="text-right">Remove</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {documents.content.map((document) => (
                      <TableRow key={document.id}>
                        <TableCell className="font-medium">
                          {document.title}
                          <span className="block text-xs text-muted-foreground">
                            {document.fileName} · {(document.sizeBytes / 1024).toFixed(0)} KB
                          </span>
                        </TableCell>
                        <TableCell>
                          <StatusBadge status={document.status} />
                        </TableCell>
                        <TableCell className="text-right">{document.chunkCount}</TableCell>
                        <TableCell className="max-w-[16rem] truncate text-xs text-muted-foreground">
                          {document.errorMessage ?? "—"}
                        </TableCell>
                        <TableCell className="text-right">
                          <DeleteDocument
                            projectId={params.projectId!}
                            documentId={document.id}
                            title={document.title}
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Project settings</CardTitle>
              <CardDescription>Renaming does not change the slug, so links keep working.</CardDescription>
            </CardHeader>
            <settingsFetcher.Form method="post">
              <CardContent className="space-y-4">
                <input type="hidden" name="intent" value="update-project" />
                <div className="space-y-2">
                  <Label htmlFor="project-name">Name</Label>
                  <Input id="project-name" name="name" defaultValue={project.name} required />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="project-description">Description</Label>
                  <Textarea
                    id="project-description"
                    name="description"
                    defaultValue={project.description ?? ""}
                    rows={4}
                  />
                </div>
                {settingsError && !settingsError.violations.length && (
                  <p className="text-xs text-destructive">{settingsError.message}</p>
                )}
              </CardContent>
              <CardFooter>
                <SubmitButton pending={settingsFetcher.state !== "idle"} variant="outline" className="w-full">
                  Save project
                </SubmitButton>
              </CardFooter>
            </settingsFetcher.Form>
            <Separator />
            <CardContent className="pt-6">
              <DeleteProject
                projectId={params.projectId!}
                name={project.name}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>How ingestion works</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm text-muted-foreground">
              <p>1. The file is stored as a document row in Postgres, marked pending.</p>
              <p>2. Text is extracted per format, then split into overlapping chunks.</p>
              <p>3. Each chunk is embedded and written to pgvector with project metadata.</p>
              <p>4. The row is marked indexed with its chunk count, or failed with the reason.</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}

function DeleteDocument({
  projectId,
  documentId,
  title,
}: {
  projectId: string;
  documentId: string;
  title: string;
}) {
  const fetcher = useFetcher<ActionResult<{ documentId: string }>>();

  const deleted = fetcher.data?.ok;
  useEffect(() => {
    if (deleted) toast.success("Document and its embeddings deleted");
  }, [deleted]);

  return (
    <fetcher.Form
      method="post"
      onSubmit={(event) => {
        if (!confirm(`Delete '${title}' and its embeddings?`)) event.preventDefault();
      }}
    >
      <input type="hidden" name="intent" value="delete-document" />
      <input type="hidden" name="documentId" value={documentId} />
      <input type="hidden" name="projectId" value={projectId} />
      <SubmitButton pending={fetcher.state !== "idle"} size="sm" variant="ghost">
        <Trash2Icon data-icon="inline-start" />
        Delete
      </SubmitButton>
    </fetcher.Form>
  );
}

function DeleteProject({
  projectId,
  name,
}: {
  projectId: string;
  name: string;
}) {
  const fetcher = useFetcher<ActionResult<{ deleted: boolean }>>();

  return (
    <fetcher.Form
      method="post"
      className="space-y-3"
      onSubmit={(event) => {
        if (!confirm(`Delete '${name}', its documents and its conversations?`)) event.preventDefault();
      }}
    >
      <p className="text-sm text-muted-foreground">
        Deletes the project, every ingested document and all of its conversations.
      </p>
      <input type="hidden" name="intent" value="delete-project" />
      <input type="hidden" name="projectId" value={projectId} />
      <SubmitButton pending={fetcher.state !== "idle"} size="sm" variant="destructive" className="w-full">
        <ArchiveIcon data-icon="inline-start" />
        Delete project
      </SubmitButton>
      {errorOf(fetcher.data) && (
        <p className="text-xs text-destructive">{errorOf(fetcher.data)?.message}</p>
      )}
    </fetcher.Form>
  );
}
