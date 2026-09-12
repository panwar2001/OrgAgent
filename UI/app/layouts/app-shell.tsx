import { ActivityIcon, Building2Icon, LayoutDashboardIcon, ShieldAlertIcon } from "lucide-react";
import { Link, NavLink, Outlet, useLoaderData, type LoaderFunctionArgs } from "react-router";

import { UserMenu } from "~/components/user-menu";
import { Button } from "~/components/ui/button";
import { requireUser, authConfig } from "~/lib/auth.server";
import { apiBaseUrl, workerEnv } from "~/lib/api/server";
import { cn } from "~/lib/utils";

export async function loader(args: LoaderFunctionArgs) {
  const env = workerEnv(args.context);
  // Sign-in is required only once Clerk keys are configured; until then the console is open.
  const user = await requireUser(args);
  return { apiBaseUrl: apiBaseUrl(args.context), signedIn: Boolean(user), authConfigured: Boolean(authConfig(env)) };
}

const NAV = [
  { to: "/", label: "Dashboard", icon: LayoutDashboardIcon, end: true },
  { to: "/organizations", label: "Organizations", icon: Building2Icon, end: false },
];

export default function AppShell() {
  const { apiBaseUrl, authConfigured } = useLoaderData<typeof loader>();

  return (
    <div className="grid min-h-svh grid-cols-1 lg:grid-cols-[16rem_1fr]">
      <aside className="flex flex-col gap-6 border-b bg-sidebar p-4 text-sidebar-foreground lg:border-r lg:border-b-0">
        <div className="flex items-center gap-2">
          <div className="flex size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
            <ActivityIcon className="size-4" />
          </div>
          <div className="leading-tight">
            <p className="font-heading text-sm font-semibold">OrgAgent</p>
            <p className="text-xs text-muted-foreground">RAG console</p>
          </div>
        </div>

        <nav className="flex flex-col gap-1">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors",
                  isActive
                    ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
                    : "hover:bg-sidebar-accent/60",
                )
              }
            >
              <item.icon className="size-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="mt-auto space-y-3">
          {!authConfigured && (
            <div className="space-y-1.5 rounded-md border border-dashed border-sidebar-border p-3 text-xs">
              <p className="flex items-center gap-1.5 font-medium">
                <ShieldAlertIcon className="size-3.5" />
                Sign-in not configured
              </p>
              <p className="text-muted-foreground">
                Anyone with the URL can use this console, and the API behind it is open too.
              </p>
              <Button asChild size="sm" variant="outline" className="w-full">
                <Link to="/login">Set up Google sign-in</Link>
              </Button>
            </div>
          )}

          <div className="space-y-1 rounded-md border border-sidebar-border p-3 text-xs">
            <p className="font-medium">Backend</p>
            <p className="break-all text-muted-foreground">{apiBaseUrl}</p>
          </div>
        </div>
      </aside>

      <div className="flex min-w-0 flex-col">
        <header className="flex items-center justify-end gap-2 border-b bg-background/80 px-4 py-2.5 backdrop-blur lg:px-6">
          <UserMenu authConfigured={authConfigured} />
        </header>
        <main className="min-w-0 flex-1 p-4 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
