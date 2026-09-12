import { SignIn } from "@clerk/react-router";
import { KeyRoundIcon } from "lucide-react";
import { Link, useLoaderData, type LoaderFunctionArgs } from "react-router";

import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "~/components/ui/card";
import { workerEnv } from "~/lib/api/server";
import { authConfig, clerkPublishableKey } from "~/lib/auth.server";

export async function loader({ context }: LoaderFunctionArgs) {
  const env = workerEnv(context);
  return {
    publishableKey: clerkPublishableKey(env) ?? null,
    configured: Boolean(authConfig(env)),
  };
}

/**
 * Clerk renders sign-in, sign-up, Google (and whatever else you enable in its dashboard) here, so
 * there is nothing to build and no Google Cloud console to configure.
 */
export default function Login() {
  const { configured } = useLoaderData<typeof loader>();

  if (configured) {
    return (
      <main className="flex min-h-svh items-center justify-center bg-muted/30 p-6">
        <SignIn />
      </main>
    );
  }

  return (
    <main className="flex min-h-svh items-center justify-center bg-muted/30 p-6">
      <Card className="w-full max-w-lg">
        <CardHeader>
          <div className="mb-2 flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <KeyRoundIcon className="size-5" />
          </div>
          <CardTitle>Sign-in is not configured yet</CardTitle>
          <CardDescription>
            The console uses Clerk, which brings its own Google credentials — no Google Cloud console
            setup, no client secret.
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4 text-sm">
          <ol className="list-decimal space-y-2 pl-5">
            <li>
              Create a free application at{" "}
              <a className="text-primary underline underline-offset-2" href="https://dashboard.clerk.com/sign-up">
                dashboard.clerk.com
              </a>
              .
            </li>
            <li>Enable the Google provider (Clerk's shared credentials work out of the box).</li>
            <li>Copy the two keys from the dashboard into the Worker's environment.</li>
          </ol>

          <pre className="overflow-x-auto rounded-md bg-muted p-3 text-xs">{`# UI/.dev.vars
CLERK_PUBLISHABLE_KEY=pk_test_…
CLERK_SECRET_KEY=sk_test_…

# deployment
npx wrangler secret put CLERK_SECRET_KEY
npx wrangler secret put CLERK_PUBLISHABLE_KEY`}</pre>

          <Alert>
            <AlertTitle>Until then the console is unsigned</AlertTitle>
            <AlertDescription>
              The API this console talks to is open as well, so nothing is being enforced yet.
            </AlertDescription>
          </Alert>

          <Button asChild variant="outline" className="w-full">
            <Link to="/">Continue to the console</Link>
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
