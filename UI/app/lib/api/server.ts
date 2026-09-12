import { data } from "react-router";
import type { RouterContextProvider } from "react-router";

import { cloudflareContext } from "~/context";

import { ApiError, createApiClient, type ApiClient } from "./client";

const FALLBACK_BASE_URL = "http://127.0.0.1:8080";

/** Where the backend lives for this request: a Wrangler var, else the local default. */
export function apiBaseUrl(context: Readonly<RouterContextProvider>): string {
  return context.get(cloudflareContext)?.env?.API_BASE_URL ?? FALLBACK_BASE_URL;
}

/** An API client bound to the backend URL configured for the current Worker. */
export function api(context: Readonly<RouterContextProvider>): ApiClient {
  return createApiClient(apiBaseUrl(context));
}

/**
 * Turns a thrown error into something a route error boundary can serialise back to the browser.
 *
 * Loaders throw the result, so `isRouteErrorResponse(error)` in the boundary carries the code,
 * message and field violations of the original backend failure.
 */
export function apiFailure(error: unknown): never {
  if (error instanceof ApiError) {
    throw data(error.toBody(), { status: error.status });
  }
  throw error;
}

/** Runs a loader body, translating backend failures into route errors. */
export async function load<T>(work: () => Promise<T>): Promise<T> {
  try {
    return await work();
  } catch (error) {
    return apiFailure(error);
  }
}

/**
 * Result of an action: actions prefer reporting failures over throwing them, so forms can keep
 * the user's input and show the backend's field violations inline.
 */
export type ActionResult<T> = { ok: true; data: T } | { ok: false; error: ApiErrorBodyLike };

export interface ApiErrorBodyLike {
  status: number;
  code: string;
  message: string;
  violations: { field: string; message: string; rejectedValue?: unknown }[];
}

export async function attempt<T>(work: () => Promise<T>): Promise<ActionResult<T>> {
  try {
    return { ok: true, data: await work() };
  } catch (error) {
    if (error instanceof ApiError) {
      return {
        ok: false,
        error: {
          status: error.status,
          code: error.code,
          message: error.message,
          violations: error.violations,
        },
      };
    }
    return {
      ok: false,
      error: {
        status: 500,
        code: "UNEXPECTED_ERROR",
        message: error instanceof Error ? error.message : "Unexpected error",
        violations: [],
      },
    };
  }
}
