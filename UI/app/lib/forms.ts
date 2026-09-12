import type { ActionResult, ApiErrorBodyLike } from "~/lib/api/server";
import type { FieldViolation } from "~/lib/api/types";

/** An action failure built locally, for input the browser should not have sent. */
export function failure(
  code: string,
  message: string,
  violations: FieldViolation[] = [],
  status = 400,
): ActionResult<never> {
  return { ok: false, error: { status, code, message, violations } };
}

/** The error an action (or fetcher) reported, if it failed. */
export function errorOf(result: unknown): ApiErrorBodyLike | undefined {
  if (!result || typeof result !== "object" || !("ok" in result)) {
    return undefined;
  }
  const candidate = result as { ok?: unknown; error?: ApiErrorBodyLike };
  return candidate.ok === false ? candidate.error : undefined;
}

/** The backend's message for one field of a failed action. */
export function fieldError(result: unknown, field: string): string | undefined {
  return errorOf(result)?.violations.find((violation) => violation.field === field)?.message;
}
