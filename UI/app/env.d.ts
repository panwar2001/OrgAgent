/**
 * Bindings that are configured per environment rather than declared in wrangler.jsonc.
 *
 * Google sign-in stays off until GOOGLE_CLIENT_ID is present, so both are optional:
 *   .dev.vars locally, and `wrangler secret put` for SESSION_SECRET in a deployment.
 */
declare global {
  interface Env {
    GOOGLE_CLIENT_ID?: string;
    SESSION_SECRET?: string;
  }
}

export {};
