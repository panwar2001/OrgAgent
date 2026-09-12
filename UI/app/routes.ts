import { type RouteConfig, index, layout, route } from "@react-router/dev/routes";

export default [
  route("sign-in/*", "routes/sign-in.tsx"),
  route("sign-up/*", "routes/sign-up.tsx"),
  layout("layouts/app-shell.tsx", [
    index("routes/dashboard.tsx"),
    route("setup", "routes/setup.tsx"),
    route("projects/:projectId", "routes/project.tsx"),
    route("chat", "routes/chat.tsx"),
  ]),
] satisfies RouteConfig;
