import { getAuth } from "@clerk/react-router/server";
import { redirect, type LoaderFunctionArgs } from "react-router";

/**
 * Sign-in for the console, backed by Clerk.
 *
 * Clerk owns the provider round trip, the session and its refresh, and the sign-in UI, so there is
 * no Google Cloud console to configure and no client secret of ours to store.
 *
 * Scope is still the UI. The Spring Boot API is open, so a session decides which console you see,
 * not what the API may do. When the API enforces authorization, forward Clerk's session token to it
 * as a bearer token; Spring Security can verify it against Clerk's JWKS.
 */

export interface SessionUser {
  userId: string;
}

/**
 * The signed-in user.
 *
 * @throws a redirect to Clerk's sign-in page, remembering where the visitor was heading
 */
export async function requireUser(args: LoaderFunctionArgs): Promise<SessionUser> {
  const { userId } = await getAuth(args);
  if (!userId) {
    const url = new URL(args.request.url);
    throw redirect(`/sign-in?next=${encodeURIComponent(`${url.pathname}${url.search}`)}`);
  }
  return { userId };
}
