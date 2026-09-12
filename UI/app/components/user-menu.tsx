import { ChevronDownIcon, ExternalLinkIcon, LogOutIcon, UserIcon } from "lucide-react";
import { Form } from "react-router";

import { Avatar, AvatarFallback, AvatarImage } from "~/components/ui/avatar";
import { Badge } from "~/components/ui/badge";
import { Button } from "~/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "~/components/ui/dropdown-menu";
import type { SessionUser } from "~/lib/auth.server";

interface UserMenuProps {
  user: SessionUser | null;
  authConfigured: boolean;
}

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

/** Who is signed in, and the way out. Falls back to a clear "not configured" state. */
export function UserMenu({ user, authConfigured }: UserMenuProps) {
  if (!user) {
    return (
      <Button asChild size="sm" variant="outline">
        <a href="/login">
          <UserIcon data-icon="inline-start" />
          {authConfigured ? "Sign in" : "Sign-in not configured"}
        </a>
      </Button>
    );
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button size="sm" variant="ghost" className="gap-2">
          <Avatar className="size-6">
            {user.picture && <AvatarImage src={user.picture} alt="" />}
            <AvatarFallback className="text-[10px]">{initials(user.name)}</AvatarFallback>
          </Avatar>
          <span className="hidden max-w-32 truncate sm:inline">{user.name}</span>
          <ChevronDownIcon className="size-3.5 opacity-60" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel className="space-y-1">
          <p className="truncate text-sm font-medium">{user.name}</p>
          <p className="truncate text-xs font-normal text-muted-foreground">{user.email}</p>
          <Badge variant="outline" className="mt-1">
            Google account
          </Badge>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <a href="https://myaccount.google.com/" rel="noreferrer" target="_blank">
            <ExternalLinkIcon />
            Manage Google account
          </a>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild variant="destructive">
          <Form method="post" action="/logout" className="w-full">
            <button type="submit" className="flex w-full items-center gap-2">
              <LogOutIcon />
              Sign out
            </button>
          </Form>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
