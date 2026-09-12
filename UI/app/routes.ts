import { type RouteConfig, index, layout, route } from "@react-router/dev/routes";

export default [
  route("login", "routes/login.tsx"),
  route("logout", "routes/logout.tsx"),
  route("organizations/:organizationId/chat", "routes/chat.tsx"),
  layout("layouts/app-shell.tsx", [
    index("routes/dashboard.tsx"),
    route("organizations", "routes/organizations.tsx"),
    route("organizations/:organizationId", "routes/organization.tsx"),
    route("organizations/:organizationId/projects/:projectId", "routes/project.tsx"),
  ]),
] satisfies RouteConfig;
