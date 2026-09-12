# OrgAgent UI

The web console for the OrgAgent backend: manage organizations and projects, ingest documents,
and chat with a project's documents through the RAG pipeline.

- **React Router v8** in framework mode (SSR, loaders/actions, typed routes)
- **Cloudflare Workers** runtime via `@cloudflare/vite-plugin`, deployed with Wrangler
- **shadcn/ui** components on Tailwind CSS v4
- All backend calls happen in **loaders and actions on the server**, so the browser never talks to
  the API directly and no CORS configuration is needed

## Getting started

```bash
npm install                     # also generates worker types (postinstall)

# Tell the dev Worker where the backend is
cp .dev.vars.example .dev.vars  # API_BASE_URL=http://127.0.0.1:8080

npm run dev                     # http://localhost:5173
```

The backend (see `../Backend`) must be running for data to appear:

```bash
cd ../Backend && docker compose up --build
```

### No backend handy?

A mock backend that implements the same contract is included, so the UI is fully usable without
Postgres, Redis or a Gemini key:

```bash
npm run mock      # http://127.0.0.1:8080
```

It answers organizations, projects, documents, conversations, live window, history and
`POST /chat`, all with the real DTO shapes.

## Configuration

The backend URL is a Wrangler variable, read per request by the loaders:

| Where | What |
| --- | --- |
| `.dev.vars` | local development (`API_BASE_URL=...`) |
| `wrangler.jsonc` → `vars` | deployed default |
| `wrangler secret put API_BASE_URL` | per-environment override |

`API_BASE_URL` ends up in `context.get(cloudflareContext).env` — see `app/context.ts` and
`workers/app.ts`. The sidebar shows the value the current Worker is using, which makes a
misconfigured deployment obvious at a glance.

## Scripts

| Script | Purpose |
| --- | --- |
| `npm run dev` | dev server, server code runs in workerd |
| `npm run build` | production build (client + SSR) |
| `npm run preview` | build and serve the built Worker locally |
| `npm run deploy` | build and `wrangler deploy` |
| `npm run typecheck` | worker types, route types, `tsc -b` |
| `npm run mock` | mock backend for UI work |

## Routes

| Route | What it does |
| --- | --- |
| `/` | dashboard: organization counts, recent accounts, backend reachability |
| `/organizations` | list and create organizations |
| `/organizations/:organizationId` | rename, suspend/activate, delete; list and create projects |
| `/organizations/:organizationId/projects/:projectId` | ingest documents, watch ingestion status, edit or delete the project |
| `/organizations/:organizationId/projects/:projectId/chat` | ask questions, see the sources, browse conversations, compare the Redis window with the Postgres log |

## How it talks to the backend

```
browser ──▶ Worker (loader / action) ──▶ Spring Boot API
```

- `app/lib/api/client.ts` — typed client, one place that turns HTTP failures into `ApiError`
  (including `BACKEND_UNREACHABLE` when the backend is down) and knows every endpoint.
- `app/lib/api/server.ts` — resolves `API_BASE_URL` from the Worker context, plus the helpers that
  turn loader failures into route error responses and action failures into form errors.
- `app/lib/api/types.ts` — the wire DTOs, mirroring the Java records one to one.

Reads go through loaders, writes through actions submitted with `useFetcher`, so forms keep their
input and show the backend's field violations inline. When the backend is unreachable, pages render
the error boundary with the failure code instead of crashing, and the dashboard stays up as a health
page.

## Deploying to Cloudflare Workers

```bash
npx wrangler login
# point the deployed UI at a reachable backend
npx wrangler secret put API_BASE_URL     # e.g. https://api.example.com
npm run deploy
```

`wrangler.jsonc` declares `main: ./workers/app.ts`, the compatibility date, `nodejs_compat` and
observability. `vite build` writes the deployable config to `build/server/wrangler.json`
(worker entry plus `assets.directory: build/client`), which is how `wrangler deploy` finds the
built Worker — the same configuration Cloudflare's own autoconfiguration generates for React Router.

## Verification

Checked against the mock backend through the built Worker (`wrangler dev`):

- `/`, `/organizations`, a project page and a chat page all render live backend data (200)
- `POST` to the chat route reaches the backend and renders the answer with its sources
- with the backend down, routes return 503 and the error boundary renders the code and message
- `npm run typecheck` and `npm run build` are clean

Not covered: a real Postgres/Redis/Gemini backend (the UI's contract is verified against the mock,
which mirrors the DTOs) and a real `wrangler deploy`, which needs a Cloudflare account.

## Component library

`app/components/ui` holds the shadcn/ui components (button, card, input, label, textarea, badge,
table, separator, skeleton, alert, tabs, select, dialog, dropdown-menu, sonner, scroll-area).
Some are not used yet — they are there to build on. Add more with:

```bash
npx shadcn@latest add <component>
```
