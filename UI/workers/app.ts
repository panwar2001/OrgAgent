import { createRequestHandler, RouterContextProvider } from "react-router";

import { cloudflareContext } from "~/context";

const requestHandler = createRequestHandler(
  () => import("virtual:react-router/server-build"),
  import.meta.env.MODE,
);

export default {
  async fetch(request, env, ctx) {
    // Loaders and actions read this with `context.get(cloudflareContext)`.
    const context = new RouterContextProvider();
    context.set(cloudflareContext, { env, ctx });

    return requestHandler(request, context);
  },
} satisfies ExportedHandler<Env>;
