import { ActivityIcon, FolderKanbanIcon, MessagesSquareIcon } from "lucide-react";
import { Link, NavLink, Outlet, useLoaderData, type LoaderFunctionArgs } from "react-router";

import { UserMenu } from "~/components/user-menu";
import { Badge } from "~/components/ui/badge";
import { StatusBadge } from "~/components/status-badge";
import { apiBaseUrl, workerEnv } from "~/lib/api/server";
import { findOrganization } from "~/lib/organization.server";

export async function loader(args: LoaderFunctionArgs) {
  // One organization per sign-in: this is the tenant the whole console operates on.
  const organization = await findOrganization(args);
  return { apiBaseUrl: apiBaseUrl(args.context), organization };
}

const NAV = [
  { to: "/", label: "Dashboard", icon: ActivityIcon, end: true },
  { to: "/chat", label: "Chat", icon: MessagesSquareIcon, end: false },
];

export default function AppShell() {
  const { apiBaseUrl, organization } = useLoaderData<typeof loader>();

  return (
    <div className="grid min-h-svh grid-cols-1 lg:grid-cols-[16rem_1fr]">
      <aside className="flex flex-col gap-6 border-b bg-sidebar p-4 text-sidebar-foreground lg:border-r lg:border-b-0">
        <Link to="/" className="flex items-center gap-2">
          <div className="flex size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
            <ActivityIcon className="size-4" />
          </div>
          <div className="leading-tight">
            <p className="font-heading text-sm font-semibold">OrgAgent</p>
            <p className="text-xs text-muted-foreground">RAG console</p>
          </div>
        </Link>

        <nav className="flex flex-col gap-1">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `relative flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? "font-medium text-sidebar-accent-foreground before:absolute before:top-1.5 before:bottom-1.5 before:-left-4 before:w-0.5 before:rounded-full before:bg-sidebar-primary"
                    : "text-sidebar-foreground/80 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground"
                }`
              }
            >
              <item.icon className="size-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="mt-auto space-y-3">
          <div className="space-y-1.5 rounded-md border border-sidebar-border p-3 text-xs">
            <p className="text-muted-foreground">Organization</p>
            {organization ? (
              <>
                <p className="flex items-center gap-1.5">
                  <span className="truncate font-medium text-foreground">{organization.name}</span>
                  <StatusBadge status={organization.status} />
                </p>
              </>
            ) : (
              <>
                <p className="font-medium text-foreground">Not set up yet</p>
                <Link to="/setup" className="text-primary underline underline-offset-2">
                  Name your organization
                </Link>
              </>
            )}
          </div>

          <p className="truncate px-1 text-[10px] text-muted-foreground/70" title={apiBaseUrl}>
            API {apiBaseUrl}
          </p>
        </div>
      </aside>

      <div className="flex min-w-0 flex-col">
        <header className="flex items-center justify-end gap-2 border-b bg-background/80 px-4 py-2.5 backdrop-blur lg:px-6">
          <UserMenu />
        </header>
        <main className="min-w-0 flex-1 p-4 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
