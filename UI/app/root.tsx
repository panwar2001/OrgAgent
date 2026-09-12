import { ClerkProvider } from "@clerk/react-router";
import { shadcn } from "@clerk/ui/themes";
import { clerkMiddleware, rootAuthLoader } from "@clerk/react-router/server";
import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
  useRouteError,
  type LoaderFunctionArgs,
} from "react-router";

import { RouteError } from "~/components/route-error";
import { Toaster } from "~/components/ui/sonner";
import "./app.css";

// Follow the OS colour scheme: the shadcn tokens switch on the `.dark` class.
const THEME_SCRIPT = `try{if(window.matchMedia("(prefers-color-scheme: dark)").matches){document.documentElement.classList.add("dark")}}catch(e){}`;

/** Clerk attaches the session to the request here, for every route. */
export const middleware = [clerkMiddleware()];

/** Required: without it, getAuth() throws in nested loaders. */
export function loader(args: LoaderFunctionArgs) {
  return rootAuthLoader(args);
}

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>OrgAgent</title>
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
        <Meta />
        <Links />
      </head>
      <body className="min-h-svh antialiased">
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

export default function App({ loaderData }: { loaderData: Awaited<ReturnType<typeof loader>> }) {
  return (
    <ClerkProvider appearance={{ theme: shadcn }} loaderData={loaderData}>
      <Outlet />
      <Toaster position="top-right" />
    </ClerkProvider>
  );
}

export function ErrorBoundary() {
  const error = useRouteError();

  // A loader error that is not a Response (a bug in a loader) still gets a usable page.
  const fallback = !isRouteErrorResponse(error) && error instanceof Error ? error : undefined;

  return (
    <main className="mx-auto w-full max-w-3xl p-6">
      <RouteError error={error} fallback={fallback} />
    </main>
  );
}
