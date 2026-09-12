import { UserButton } from "@clerk/react-router";

/**
 * Who is signed in, and the way out. Clerk's own button renders the avatar, the profile and
 * sign-out, so there is nothing custom to maintain here.
 */
export function UserMenu() {
  return <UserButton />;
}
