import { redirect, type ActionFunctionArgs } from "react-router";

import { authConfig, sessionCookie } from "~/lib/auth.server";
import { workerEnv } from "~/lib/api/server";

export async function action({ context }: ActionFunctionArgs) {
  const config = authConfig(workerEnv(context));
  const headers = config
    ? { "Set-Cookie": await sessionCookie(config).serialize("", { maxAge: 0 }) }
    : undefined;
  return redirect("/login", { headers });
}

export async function loader() {
  return redirect("/");
}
