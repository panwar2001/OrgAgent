import { createContext } from "react-router";

/**
 * Values the Worker entry puts on the per-request context.
 *
 * React Router 8 passes load context through a `RouterContextProvider` rather than a plain
 * object, so each value is stored under a typed context key. The default of `undefined` keeps
 * `context.get()` safe on the client, where no Worker environment exists.
 */
export interface CloudflareRequestContext {
  env: Env;
  ctx: ExecutionContext;
}

export const cloudflareContext = createContext<CloudflareRequestContext | undefined>(undefined);
