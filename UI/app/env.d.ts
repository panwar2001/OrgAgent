/**
 * Bindings configured per environment rather than in wrangler.jsonc.
 *
 * Clerk's publishable key is public and may be a plain var; the secret key must be a secret
 * (`wrangler secret put CLERK_SECRET_KEY`). Sign-in stays off, and the console stays usable,
 * until both are present.
 */
declare global {
  interface Env {
    CLERK_PUBLISHABLE_KEY?: string;
    CLERK_SECRET_KEY?: string;
  }
}

export {};
