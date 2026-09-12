import { Building2Icon, PlusIcon } from "lucide-react";
import { useEffect, useRef } from "react";
import {
  Link,
  useFetcher,
  useLoaderData,
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "~/components/ui/table";
import type { ActionResult } from "~/lib/api/server";
import { api, attempt, load } from "~/lib/api/server";
import type { Organization } from "~/lib/api/types";
import { errorOf, failure, fieldError } from "~/lib/forms";

export async function loader({ context, request }: LoaderFunctionArgs) {
  const url = new URL(request.url);
  const page = Number(url.searchParams.get("page") ?? "0");
  const size = Number(url.searchParams.get("size") ?? "20");

  return load(async () => ({
    organizations: await api(context).listOrganizations({ page, size }),
  }));
}

export async function action({ context, request }: ActionFunctionArgs) {
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "create");

  if (intent === "create") {
    const name = String(form.get("name") ?? "").trim();
    const slug = String(form.get("slug") ?? "").trim();
    if (!name) {
      return failure("VALIDATION_FAILED", "Request validation failed", [
        { field: "name", message: "must not be blank" },
      ]);
    }
    return attempt(() => api(context).createOrganization({ name, slug: slug || undefined }));
  }

  return failure("INVALID_REQUEST", `Unknown intent '${intent}'`);
}

export default function Organizations() {
  const { organizations } = useLoaderData<typeof loader>();
  const fetcher = useFetcher<ActionResult<Organization>>();
  const formRef = useRef<HTMLFormElement>(null);

  const created = fetcher.data && fetcher.data.ok ? fetcher.data.data : undefined;
  useEffect(() => {
    if (created) {
      toast.success(`Organization '${created.name}' created`);
      formRef.current?.reset();
    }
  }, [created]);

  const error = errorOf(fetcher.data);

  return (
    <>
      <PageHeader
        title="Organizations"
        description="Each organization is a tenant: it owns projects, and every project owns its own documents and chats."
      />

      <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>All organizations</CardTitle>
            <CardDescription>
              {organizations.totalElements} total · page {organizations.page + 1} of{" "}
              {Math.max(organizations.totalPages, 1)}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {organizations.content.length === 0 ? (
              <EmptyState
                icon={Building2Icon}
                title="No organizations yet"
                description="Create one on the right to get started."
              />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Slug</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Open</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {organizations.content.map((organization) => (
                    <TableRow key={organization.id}>
                      <TableCell className="font-medium">{organization.name}</TableCell>
                      <TableCell className="text-muted-foreground">{organization.slug}</TableCell>
                      <TableCell>
                        <StatusBadge status={organization.status} />
                      </TableCell>
                      <TableCell className="text-right">
                        <Button asChild size="sm" variant="ghost">
                          <Link to={`/organizations/${organization.id}`}>Projects</Link>
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
          {organizations.totalPages > 1 && (
            <CardFooter className="justify-between">
              <Button asChild disabled={organizations.page === 0} size="sm" variant="outline">
                <Link to={`?page=${Math.max(organizations.page - 1, 0)}`}>Previous</Link>
              </Button>
              <Button
                asChild
                disabled={organizations.page + 1 >= organizations.totalPages}
                size="sm"
                variant="outline"
              >
                <Link to={`?page=${organizations.page + 1}`}>Next</Link>
              </Button>
            </CardFooter>
          )}
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>New organization</CardTitle>
            <CardDescription>A slug is derived from the name unless you provide one.</CardDescription>
          </CardHeader>
          <fetcher.Form method="post" ref={formRef}>
            <CardContent className="space-y-4">
              <input type="hidden" name="intent" value="create" />
              <div className="space-y-2">
                <Label htmlFor="name">Name</Label>
                <Input id="name" name="name" placeholder="Acme Ltd." required />
                {fieldError(fetcher.data, "name") && (
                  <p className="text-xs text-destructive">{fieldError(fetcher.data, "name")}</p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="slug">Slug (optional)</Label>
                <Input id="slug" name="slug" placeholder="acme" />
                {fieldError(fetcher.data, "slug") && (
                  <p className="text-xs text-destructive">{fieldError(fetcher.data, "slug")}</p>
                )}
              </div>
              {error && !error.violations.length && <p className="text-xs text-destructive">{error.message}</p>}
            </CardContent>
            <CardFooter>
              <SubmitButton pending={fetcher.state !== "idle"} className="w-full">
                <PlusIcon data-icon="inline-start" />
                Create organization
              </SubmitButton>
            </CardFooter>
          </fetcher.Form>
        </Card>
      </div>
    </>
  );
}
