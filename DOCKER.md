# Docker Setup

Runs the complete HelpDesk Management System (MySQL, backend, frontend) locally
with Docker Compose. This covers **local containerized startup only** — it is
not a deployment guide (there is no Phase 5 deployment work yet).

## Prerequisites

- Docker Engine 24+ and the Docker Compose plugin (`docker compose version`
  should print a v2.x version)
- Ports `8080`, `3000`, and `3306` free on your machine, or override them (see
  below)

## First-time setup

```bash
cp .env.example .env
```

Open `.env` and set at least `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, and
`JWT_SECRET` — these have no defaults and the stack will not start without
them. `JWT_SECRET` must be at least 64 characters (HS512 requirement); e.g.:

```bash
openssl rand -base64 64
```

## Build

```bash
docker compose build
```

## Start

```bash
docker compose up -d
```

First start takes longer (Maven dependency resolution, MySQL initialization).
Once healthy:

| Service  | URL                                          |
|----------|-----------------------------------------------|
| Frontend | http://localhost:3000                         |
| Backend  | http://localhost:8080/api/v1                  |
| Swagger  | http://localhost:8080/swagger-ui.html         |
| Health   | http://localhost:8080/api/v1/health           |
| MySQL    | localhost:3306 (any MySQL client)             |

Sign in with the bootstrap admin (`ADMIN_BOOTSTRAP_EMAIL`/
`ADMIN_BOOTSTRAP_PASSWORD` in your `.env`; defaults to
`admin@helpdesk.local` / `Dev0nly!AdminBootstrapPassword` if left unset).

## Stop

```bash
docker compose down
```

Add `-v` to also delete the MySQL volume (wipes all data):

```bash
docker compose down -v
```

## Rebuild

After changing backend or frontend source:

```bash
docker compose up -d --build
```

Or a single service:

```bash
docker compose up -d --build backend
```

## View logs

```bash
docker compose logs -f
docker compose logs -f backend
docker compose logs -f mysql
docker compose logs -f frontend
```

## Troubleshooting

**Backend keeps restarting / never becomes healthy**
Check `docker compose logs backend`. Almost always one of:
- `.env` wasn't created, or `JWT_SECRET`/`DB_PASSWORD` weren't set.
- `JWT_SECRET` is shorter than 64 characters — `JwtServiceImpl` rejects it at
  startup with a clear message.
- MySQL isn't healthy yet — the backend's `depends_on` condition should
  already prevent this, but check `docker compose ps` to confirm `mysql`
  shows `healthy`, not just `running`.

**Frontend loads but every request fails / CORS errors in the browser console**
`CORS_ALLOWED_ORIGINS` (backend) must match the *browser's* origin exactly,
i.e. wherever you're publishing the frontend (`FRONTEND_PORT`, default
`3000`). If you changed `FRONTEND_PORT` in `.env`, either rely on the
automatic default (`http://localhost:${FRONTEND_PORT}`) or set
`CORS_ALLOWED_ORIGINS` explicitly to match.

**Frontend calls the wrong backend URL**
The frontend container rewrites `js/core/config.js`'s API base URL from the
`API_BASE_URL` environment variable at container *start* (see
`frontend/docker/40-inject-api-base-url.sh`), not at build time. Changing
`API_BASE_URL` in `.env` requires `docker compose up -d` (recreating the
container), not just a page refresh.

**Port already in use**
Override `BACKEND_PORT`, `FRONTEND_PORT`, or `MYSQL_PORT` in `.env` to a free
port and re-run `docker compose up -d`.

**MySQL data from a previous run looks stale / schema conflicts**
The MySQL data directory persists in the `mysql_data` named volume across
restarts. `docker compose down -v` removes it for a completely clean start
(the backend's `ddl-auto: update`, in the `docker` Spring profile, recreates
the schema from the JPA entities on next startup).

**Rebuilding after a `pom.xml` change doesn't seem to pick up new dependencies**
The build stage caches dependency resolution in its own Docker layer, keyed
on `pom.xml`/the wrapper files. This is automatic — Docker invalidates that
layer whenever `pom.xml` changes. If you suspect a stale build cache anyway:

```bash
docker compose build --no-cache backend
```

## Notes on this milestone

- `application-docker.yml` is a **new** Spring profile, deliberately distinct
  from the existing `application-prod.yml`. A real production deployment
  needs an RS256 keypair and disables Swagger entirely
  (`09-Security-Operations.md` §17.6) — neither is appropriate for "clone the
  repo and run it locally," and this milestone's own verification step
  requires Swagger to stay reachable. `application-prod.yml` is untouched and
  remains what an eventual real deployment will use.
- No backend/frontend feature, API, or database schema changes were made —
  this milestone is containerization only.
