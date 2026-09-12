import { AlertTriangleIcon, ShieldCheckIcon } from "lucide-react";
import { useEffect, useRef } from "react";
import { Form, useActionData, useLoaderData, useNavigation, useSearchParams, type ActionFunctionArgs, type LoaderFunctionArgs } from "react-router";

import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "~/components/ui/card";
import { authConfig, sessionCookie, verifyGoogleIdToken } from "~/lib/auth.server";
import { workerEnv } from "~/lib/api/server";
import { redirect } from "react-router";

interface GoogleAccountsId {
  initialize: (options: { client_id: string; callback: (response: { credential: string }) => void }) => void;
  renderButton: (element: HTMLElement, options: Record<string, unknown>) => void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleAccountsId } };
  }
}

export async function loader({ context, request }: LoaderFunctionArgs) {
  const config = authConfig(workerEnv(context));
  const next = new URL(request.url).searchParams.get("next") ?? "/";
  return { clientId: config?.clientId ?? null, next };
}

export async function action({ context, request }: ActionFunctionArgs) {
  const config = authConfig(workerEnv(context));
  if (!config) {
    return { error: "Google sign-in is not configured for this deployment." };
  }

  const form = await request.formData();
  const credential = String(form.get("credential") ?? "");
  const next = String(form.get("next") ?? "/");

  try {
    const user = await verifyGoogleIdToken(credential, config.clientId);
    // Only same-site paths: `next` comes from a query string and must not become an open redirect.
    const target = next.startsWith("/") && !next.startsWith("//") ? next : "/";
    return redirect(target, { headers: { "Set-Cookie": await sessionCookie(config).serialize(user) } });
  } catch (error) {
    return { error: error instanceof Error ? error.message : "Sign-in failed" };
  }
}

export default function Login() {
  const { clientId, next } = useLoaderData<typeof loader>();
  const actionData = useActionData<typeof action>();
  const navigation = useNavigation();
  const [searchParams] = useSearchParams();
  const buttonRef = useRef<HTMLDivElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const credentialRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!clientId || !buttonRef.current) return;

    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.onload = () => {
      const googleId = window.google?.accounts.id;
      if (!googleId || !buttonRef.current) return;
      googleId.initialize({
        client_id: clientId,
        callback: (response) => {
          if (credentialRef.current) credentialRef.current.value = response.credential;
          formRef.current?.requestSubmit();
        },
      });
      googleId.renderButton(buttonRef.current, {
        type: "standard",
        theme: "outline",
        size: "large",
        text: "continue_with",
        shape: "rectangular",
        width: 320,
      });
    };
    document.head.appendChild(script);
    return () => {
      script.remove();
    };
  }, [clientId]);

  const nextTarget = searchParams.get("next") ?? next;

  return (
    <main className="flex min-h-svh items-center justify-center bg-muted/30 p-6">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-2 flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <ShieldCheckIcon className="size-5" />
          </div>
          <CardTitle>Sign in to OrgAgent</CardTitle>
          <CardDescription>
            Use the Google account your organization knows. Projects, documents and sessions are shown per
            organization.
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          {actionData?.error && (
            <Alert variant="destructive">
              <AlertTriangleIcon />
              <AlertTitle>Sign-in failed</AlertTitle>
              <AlertDescription>{actionData.error}</AlertDescription>
            </Alert>
          )}

          <Form method="post" ref={formRef} className="space-y-3">
            <input type="hidden" name="next" value={nextTarget} />
            <input type="hidden" name="credential" ref={credentialRef} />
            {clientId ? (
              <div ref={buttonRef} className="flex justify-center" />
            ) : (
              <Alert>
                <AlertTriangleIcon />
                <AlertTitle>Google sign-in is not configured</AlertTitle>
                <AlertDescription className="space-y-2">
                  <p>
                    Set <code>GOOGLE_CLIENT_ID</code> for this Worker and sign-in becomes required. Create an
                    OAuth client of type <em>Web application</em> in the Google Cloud console and add this
                    origin as an authorized JavaScript origin.
                  </p>
                  <pre className="overflow-x-auto rounded-md bg-muted p-2 text-xs">
                    echo "GOOGLE_CLIENT_ID=…apps.googleusercontent.com" &gt;&gt; .dev.vars
                  </pre>
                  <p className="text-xs">
                    Until then the console runs unsigned: the API it talks to is open as well, so nothing
                    here is being enforced yet.
                  </p>
                </AlertDescription>
              </Alert>
            )}

            {navigation.state !== "idle" && <p className="text-center text-xs text-muted-foreground">Signing in…</p>}
          </Form>
        </CardContent>
      </Card>
    </main>
  );
}
