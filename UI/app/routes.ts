import { type RouteConfig, index, layout, route } from "@react-router/dev/routes";

export default [
  layout("layouts/app-shell.tsx", [
    index("routes/dashboard.tsx"),
    route("organizations", "routes/organizations.tsx"),
    route("organizations/:organizationId", "routes/organization.tsx"),
    route("organizations/:organizationId/projects/:projectId", "routes/project.tsx"),
    route("organizations/:organizationId/chat", "routes/chat.tsx"),
  ]),
] satisfies RouteConfig;
