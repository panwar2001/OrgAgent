import { AlertTriangleIcon, RefreshCwIcon } from "lucide-react";
import { isRouteErrorResponse, Link } from "react-router";

import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Badge } from "~/components/ui/badge";
import { Button } from "~/components/ui/button";
import type { ApiErrorBody, FieldViolation } from "~/lib/api/types";

interface RouteErrorProps {
  error: unknown;
  fallback?: Error;
}

/** Renders whatever a loader or action threw, including the backend's own error codes. */
export function RouteError({ error, fallback }: RouteErrorProps) {
  let status = 500;
  let code = "UNEXPECTED_ERROR";
  let message = fallback?.message ?? "Something went wrong.";
  let violations: FieldViolation[] = [];

  if (isRouteErrorResponse(error)) {
    status = error.status;
    const body = (error.data ?? {}) as Partial<ApiErrorBody>;
    code = body.code ?? `HTTP_${error.status}`;
    message = body.message ?? error.statusText ?? message;
    violations = body.violations ?? [];
  }

  const unreachable = code === "BACKEND_UNREACHABLE";

  return (
    <Alert variant="destructive" className="mb-4">
      <AlertTriangleIcon />
      <AlertTitle className="flex items-center gap-2">
        {unreachable ? "Backend not reachable" : "Request failed"}
        <Badge variant="outline">{status}</Badge>
        <Badge variant="secondary">{code}</Badge>
      </AlertTitle>
      <AlertDescription className="space-y-3">
        <p>{message}</p>

        {violations.length > 0 && (
          <ul className="list-disc space-y-1 pl-5">
            {violations.map((violation) => (
              <li key={`${violation.field}-${violation.message}`}>
                <span className="font-medium">{violation.field}</span>: {violation.message}
              </li>
            ))}
          </ul>
        )}

        <div className="flex gap-2">
          <Button size="sm" variant="outline" onClick={() => window.location.reload()}>
            <RefreshCwIcon data-icon="inline-start" />
            Try again
          </Button>
          <Button size="sm" variant="ghost" asChild>
            <Link to="/">Back to dashboard</Link>
          </Button>
        </div>
      </AlertDescription>
    </Alert>
  );
}
