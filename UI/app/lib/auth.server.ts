import { createCookie, redirect } from "react-router";
import { createRemoteJWKSet, jwtVerify } from "jose";

/**
 * Sign-in for the console.
 *
 * Scope: this gates the UI only. The Spring Boot API is still open, so a session decides which
 * organization the console shows and who the user appears to be; it is not a security boundary yet.
 * When the API grows authorization of its own, the same ID token can be forwarded to it as a bearer
 * token from here.
 *
 * Sign-in activates as soon as GOOGLE_CLIENT_ID is configured. Without it the console stays usable
 * and says so, rather than locking people out of an app whose API is open anyway.
 */

export const SESSION_COOKIE = "orgagent_session";

/** The signed-in user, as Google describes them. */
export interface SessionUser {
  sub: string;
  email: string;
  name: string;
  picture?: string;
}

export interface AuthConfig {
  clientId: string;
  sessionSecret: string;
}

/** Google publishes the keys it signs ID tokens with; `jose` caches them. */
const googleKeys = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

export function authConfig(env: Partial<Env> | undefined): AuthConfig | undefined {
  const clientId = env?.GOOGLE_CLIENT_ID?.trim();
  if (!env || !clientId) {
    return undefined;
  }
  // With no explicit secret the cookie is still signed, using a value derived from the client id.
  // That stops hand-edited cookies but is not a substitute for a real secret: set SESSION_SECRET.
  const sessionSecret = env.SESSION_SECRET?.trim() || `derived:${clientId}`;
  return { clientId, sessionSecret };
}

export function sessionCookie(config: AuthConfig) {
  return createCookie(SESSION_COOKIE, {
    path: "/",
    httpOnly: true,
    sameSite: "lax",
    secure: true,
    secrets: [config.sessionSecret],
    maxAge: 60 * 60 * 24 * 7,
  });
}

/** Verifies a Google ID token: signature, issuer, audience and verified email. */
export async function verifyGoogleIdToken(credential: string, clientId: string): Promise<SessionUser> {
  const { payload } = await jwtVerify(credential, googleKeys, {
    issuer: ["https://accounts.google.com", "accounts.google.com"],
    audience: clientId,
  });

  const sub = typeof payload.sub === "string" ? payload.sub : undefined;
  const email = typeof payload.email === "string" ? payload.email : undefined;
  if (!sub || !email) {
    throw new Error("Google did not return an email address for this account");
  }
  if (payload.email_verified === false) {
    throw new Error("Google reports this email address as unverified");
  }

  return {
    sub,
    email,
    name: typeof payload.name === "string" ? payload.name : email,
    picture: typeof payload.picture === "string" ? payload.picture : undefined,
  };
}

/** The signed-in user, or undefined when there is no valid session. */
export async function currentUser(request: Request, env: Partial<Env> | undefined): Promise<SessionUser | undefined> {
  const config = authConfig(env);
  if (!config) {
    return undefined;
  }
  const value = await sessionCookie(config).parse(request.headers.get("Cookie"));
  if (!value || typeof value !== "object") {
    return undefined;
  }
  const candidate = value as Partial<SessionUser>;
  if (typeof candidate.email !== "string" || typeof candidate.sub !== "string") {
    return undefined;
  }
  return { sub: candidate.sub, email: candidate.email, name: candidate.name ?? candidate.email, picture: candidate.picture };
}

/** Sends an unauthenticated visitor to the sign-in page, remembering where they were going. */
export async function requireUser(request: Request, env: Partial<Env> | undefined): Promise<SessionUser | undefined> {
  const config = authConfig(env);
  if (!config) {
    return undefined;
  }
  const user = await currentUser(request, env);
  if (!user) {
    const url = new URL(request.url);
    throw redirect(`/login?next=${encodeURIComponent(`${url.pathname}${url.search}`)}`);
  }
  return user;
}
