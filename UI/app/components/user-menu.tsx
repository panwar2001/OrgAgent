import { UserButton } from "@clerk/clerk-react";
import { UserIcon } from "lucide-react";
import { Link } from "react-router";

import { Button } from "~/components/ui/button";

interface UserMenuProps {
  authConfigured: boolean;
}

/**
 * Who is signed in, and the way out.
 *
 * Clerk's own button renders the avatar, the profile and sign-out. Before Clerk is configured it is
 * replaced by a link to the setup instructions, so the console never shows a button that does
 * nothing.
 */
export function UserMenu({ authConfigured }: UserMenuProps) {
  if (!authConfigured) {
    return (
      <Button asChild size="sm" variant="outline">
        <Link to="/login">
          <UserIcon data-icon="inline-start" />
          Sign-in not configured
        </Link>
      </Button>
    );
  }

  return <UserButton />;
}
