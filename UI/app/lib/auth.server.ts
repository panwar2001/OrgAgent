import { clerkMiddleware, getAuth } from "@clerk/react-router/server";
import { redirect, type LoaderFunctionArgs, type MiddlewareFunction } from "react-router";

import { workerEnv } from "~/lib/api/server";

/**
 * Sign-in for the console, backed by Clerk.
 *
 * Clerk owns the provider round trip, the session and its refresh, and the sign-in UI, so there is
 * no Google Cloud console to configure and no client secret of ours to store: Clerk's shared Google
 * credentials work in development, and you can attach your own later.
 *
 * Scope is still the UI. The Spring Boot API is open, so a session decides which console you see,
 * not what the API will do. When the API enforces authorization, forward Clerk's session token to it
 * as a bearer token — Spring Security can verify it against Clerk's JWKS.
 *
 * Sign-in activates as soon as both Clerk keys are configured. Without them the console stays usable
 * and says so, rather than locking people out of an app whose API is open anyway.
 */

export interface AuthConfig {
  publishableKey: string;
  secretKey: string;
}

/** The signed-in identity this app needs from Clerk. */
export interface SessionUser {
  userId: string;
}

/** Clerk's public key, safe to hand to the browser. */
export function clerkPublishableKey(env: Partial<Env> | undefined): string | undefined {
  return env?.CLERK_PUBLISHABLE_KEY?.trim() || undefined;
}

export function authConfig(env: Partial<Env> | undefined): AuthConfig | undefined {
  const publishableKey = clerkPublishableKey(env);
  const secretKey = env?.CLERK_SECRET_KEY?.trim();
  return publishableKey && secretKey ? { publishableKey, secretKey } : undefined;
}

const clerk = clerkMiddleware();

/**
 * Attaches Clerk's session to the request.
 *
 * Wrapped so it is a no-op until keys are configured: `clerkMiddleware()` throws on a deployment
 * without them, and the console has to keep working before sign-in is set up.
 */
export const clerkAuthMiddleware: MiddlewareFunction<Response> = async (args, next) => {
  if (!authConfig(workerEnv(args.context))) {
    return next();
  }
  return clerk(args, next);
};

/**
 * The signed-in user, or undefined when sign-in is not configured.
 *
 * @throws a redirect to /login when sign-in is configured and the visitor has no session
 */
export async function requireUser(args: LoaderFunctionArgs): Promise<SessionUser | undefined> {
  if (!authConfig(workerEnv(args.context))) {
    return undefined;
  }

  const { userId } = await getAuth(args);
  if (!userId) {
    const url = new URL(args.request.url);
    throw redirect(`/login?next=${encodeURIComponent(`${url.pathname}${url.search}`)}`);
  }
  return { userId };
}
