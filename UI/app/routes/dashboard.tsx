import { Building2Icon, FolderKanbanIcon, ServerCrashIcon } from "lucide-react";
import { Link, useLoaderData, type LoaderFunctionArgs } from "react-router";

import { EmptyState } from "~/components/empty-state";
import { PageHeader } from "~/components/page-header";
import { StatusBadge } from "~/components/status-badge";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "~/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "~/components/ui/table";
import { isApiError } from "~/lib/api/client";
import { api } from "~/lib/api/server";
import type { ApiErrorBody, Organization, Page } from "~/lib/api/types";

type DashboardData =
  | { reachable: true; organizations: Page<Organization> }
  | { reachable: false; error: ApiErrorBody };

export async function loader({ context }: LoaderFunctionArgs): Promise<DashboardData> {
  try {
    // The dashboard is also the health page, so an unreachable backend is data, not a crash.
    return { reachable: true, organizations: await api(context).listOrganizations({ size: 100 }) };
  } catch (error) {
    if (isApiError(error)) {
      return { reachable: false, error: error.toBody() };
    }
    throw error;
  }
}

export default function Dashboard() {
  const data = useLoaderData<typeof loader>();

  if (!data.reachable) {
    return (
      <>
        <PageHeader title="Dashboard" description="Organizations, projects and their document-grounded chats." />
        <Alert variant="destructive">
          <ServerCrashIcon />
          <AlertTitle>Backend not reachable ({data.error.code})</AlertTitle>
          <AlertDescription className="space-y-2">
            <p>{data.error.message}</p>
            <pre className="overflow-x-auto rounded-md bg-muted p-3 text-xs">
              docker compose up --build{"\n"}# or point the UI at a running backend{"\n"}echo
              "API_BASE_URL=http://127.0.0.1:8080" &gt; .dev.vars
            </pre>
          </AlertDescription>
        </Alert>
      </>
    );
  }

  const organizations = data.organizations.content;
  const active = organizations.filter((organization) => organization.status === "ACTIVE").length;

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Organizations, projects and their document-grounded chats."
        actions={
          <Button asChild>
            <Link to="/organizations">Manage organizations</Link>
          </Button>
        }
      />

      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <StatCard
          icon={Building2Icon}
          label="Organizations"
          value={data.organizations.totalElements}
          hint={`${active} active`}
        />
        <StatCard icon={FolderKanbanIcon} label="Loaded here" value={organizations.length} hint="of the first page" />
        <StatCard
          icon={Building2Icon}
          label="Suspended"
          value={organizations.filter((organization) => organization.status !== "ACTIVE").length}
          hint="not accepting chats"
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Organizations</CardTitle>
          <CardDescription>The tenant accounts this console can reach.</CardDescription>
        </CardHeader>
        <CardContent>
          {organizations.length === 0 ? (
            <EmptyState
              icon={Building2Icon}
              title="No organizations yet"
              description="Create the first organization account to start a project."
              action={
                <Button asChild size="sm">
                  <Link to="/organizations">Create an organization</Link>
                </Button>
              }
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Slug</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Projects</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {organizations.slice(0, 8).map((organization) => (
                  <TableRow key={organization.id}>
                    <TableCell className="font-medium">
                      <Link className="hover:underline" to={`/organizations/${organization.id}`}>
                        {organization.name}
                      </Link>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{organization.slug}</TableCell>
                    <TableCell>
                      <StatusBadge status={organization.status} />
                    </TableCell>
                    <TableCell className="text-right">
                      <Button asChild size="sm" variant="ghost">
                        <Link to={`/organizations/${organization.id}`}>Open</Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </>
  );
}

function StatCard({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: typeof Building2Icon;
  label: string;
  value: number;
  hint: string;
}) {
  return (
    <Card>
      <CardHeader>
        <CardDescription className="flex items-center gap-2">
          <Icon className="size-4" />
          {label}
        </CardDescription>
        <CardTitle className="text-3xl">{value}</CardTitle>
      </CardHeader>
      <CardContent className="text-xs text-muted-foreground">{hint}</CardContent>
    </Card>
  );
}
