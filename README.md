# Open Link Learn

Open Link Learn is a self-hosted planner for saving multi-purpose-learning videos 
and articles, organizing them into study sessions, and tracking completed tasks.
It is built with Angular, Spring Boot, and PostgreSQL and is released under the [MIT License](LICENSE).

The application includes a searchable backlog and archive, a calendar with one-off 
and recurring sessions, configurable automatic content assignment, reminders with 
optional Web Push, and a Firefox toolbar extension for saving the active page. 

> **Security:** V1 has no login or per-user access control. Keep the service on
> localhost or behind trusted private-network access controls. Do not expose it
> directly to the public internet. See the deployment notes below before using
> it from another device.

The login feature will be added in a future phase.

For development help, see [CONTRIBUTING.md](CONTRIBUTING.md). Browser extension
documentation is in [web-extension/README.md](web-extension/README.md).

## Project status

This project is actively developed. Features and setup details may evolve; check
the plan and current source before relying on behavior in a production setup.
The backend uses Flyway migrations, seeds one local owner profile, and exposes
owner-scoped APIs without requiring a login. Content creation accepts YouTube, 
generics videos and article URLs, validates supported HTTP(S) URLs, attempts best-effort
metadata retrieval for YouTube, and falls back to a usable title when retrieval fails.

The Angular app provides backlog, dashboard, calendar, settings, and
session workflows. Sessions can reserve pending backlog items; the assignment
worker selects an eligible item at the configured cutoff without replacing a
manual assignment. Reminder jobs are rescheduled with session changes. Web Push
is disabled by default and requires VAPID keys and a registered browser.

### Prerequisites

- Docker Desktop with Docker Compose support
- For backend development outside Docker: JDK 17 and Maven 3.9 or newer
- For frontend development: Node.js 24 and npm

### Start the full stack

In the terminal, create your local environment file and start the application:

```
cp .env.example .env
docker compose up --build -d
docker compose ps
```

Open `http://localhost:8080`. Nginx serves the Angular app and forwards `/api`
requests to the backend. Compose waits for PostgreSQL health before starting the
backend, then waits for backend readiness before starting the web service. The
frontend is published only on `127.0.0.1` by default; set `APP_PORT` in `.env`
to change its local port. PostgreSQL remains published only on `127.0.0.1` and
its data remains in the `postgres_data` named volume. Backend and Actuator ports
are not published on the host.

This application has no login or per-user access control. Keep it bound to the
local machine and do not expose it directly to the internet. Remote access is
only appropriate on a trusted private network with network-level access control;
use HTTPS for hostnames and devices, and do not treat CORS as access control.
The API allows browser requests only from configured `APP_ALLOWED_ORIGIN` values
(default `http://localhost:4200` for frontend development); the Compose web app
uses same-origin requests through Nginx. CORS is not an access-control system.

The Compose healthchecks are available with `docker compose ps`. For
development outside containers, `docker compose up -d db` starts only
PostgreSQL; the existing Maven and Angular commands below run the backend and
frontend directly.

Stop the service while preserving its data with:

```
docker compose down
```

To also permanently delete the database volume, run `docker compose down -v`.
Do this only when you intend to erase local database data.

## PostgreSQL backup and test restore

The Compose database reads its database name and user from `.env`; commands run
inside the database container use those same values without putting the password
on the command line. A custom-format dump can be copied to a host backup folder:

```
backupFolder="$HOME/Backups"
mkdir -p "$backupFolder"
backupFile="$backupFolder/open-link-learn-$(date +'%Y%m%d-%H%M%S').dump"
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/open-link-learn-backup.dump'
docker compose cp db:/tmp/open-link-learn-backup.dump "$backupFile"
docker compose exec -T db rm -f /tmp/open-link-learn-backup.dump
echo "Backup saved to: $backupFile"
```

Keep the dump outside the repository, restrict access to it, and copy it to
protected storage. The database volume is persistence, not a backup. To check a
dump without touching the application database, restore it into a new, unused
test database. `createdb` fails if the selected name already exists; do not use
the application database name or `pg_restore --clean`:

```
restoreDb='open_link_learn_restore_test'
docker compose cp "$backupFile" db:/tmp/open-link-learn-restore.dump
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" --owner="$POSTGRES_USER" "$1"' -- "$restoreDb"
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" --dbname="$1" --no-owner --no-privileges --exit-on-error /tmp/open-link-learn-restore.dump' -- "$restoreDb"
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM content_item;"' -- "$restoreDb"
```

Check the restored counts against the source, then remove only the test database
created for this check and the temporary container dump:

```powershell
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" "$1"' -- $restoreDb
docker compose exec -T db rm -f /tmp/open-link-learn-restore.dump
```

For an isolated restore drill, use a separate Compose project and its own
database volume; never run `docker compose down -v` against the active project
as part of a restore test.

## Run the backend

Start PostgreSQL first, then run these commands from the repository root:

```
mvn -f backend/pom.xml test
mvn -f backend/pom.xml spring-boot:run
```

The API reads the settings from the seeded profile at
`http://localhost:8080/api/v1/settings`. Health is available at
`http://localhost:8080/actuator/health`; readiness, including PostgreSQL
availability, is at `http://localhost:8080/actuator/health/readiness`. Only the
health endpoint is exposed, and health responses contain status only, not
component names or details. Operational console logs use structured JSON and
push delivery summaries omit subscription endpoints and key material. The
backend defaults match the local values in `.env.example`; override `DB_HOST`,
`POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, or `POSTGRES_PASSWORD` in the
process environment as needed.

API errors use `application/problem+json` with `type`, `title`, `status`,
`detail`, and `instance` fields. Validation, not-found, and conflict responses
preserve safe request details; unexpected server errors return a generic detail
without exposing exception messages or stack traces.

Open the interactive API reference at `http://localhost:8080/swagger-ui/index.html`
or fetch the OpenAPI 3 document at `http://localhost:8080/v3/api-docs`. The
reference is generated from the current backend controllers. The project remains
in development; exists operational limitations and
acceptance criteria that still require end-to-end verification.

## Run the frontend

From the repository root:

```
npm --prefix frontend ci
npm --prefix frontend start
```

The development server runs at `http://localhost:4200` and proxies `/api` to the
backend at `http://localhost:8080`. Build and test with:

```
npm --prefix frontend run build
npm --prefix frontend test -- --watch=false
```

The production build includes the installable web app manifest and Angular
service worker. The worker caches the app shell and declared static assets only;
API routes are excluded from navigation fallback and no API response cache is
configured. It is disabled under `ng serve` and registers in production builds.
Browsers treat `http://localhost` and loopback addresses as secure contexts, so
you can verify installation locally when serving the production build there.
Web Push and service workers require a secure context: localhost/loopback HTTP is
allowed for local testing, but another hostname or a device on your network must
use HTTPS. Plain HTTP on those hosts cannot install the service worker or use Web
Push.

After a production build, verify the generated worker, manifest, icon, and cache
policy with:

```
npm --prefix frontend run build
npm --prefix frontend run verify:pwa
```

## Firefox extension

The extension source is in [web-extension/firefox](web-extension/firefox). It
adds the current HTTP(S) page to the backlog after an explicit toolbar action.
To load it temporarily, open `about:debugging#/runtime/this-firefox` in Firefox,
choose **Load Temporary Add-on...**, and select
`web-extension/firefox/manifest.json`. Temporary add-ons are removed when
Firefox restarts; release distribution requires Mozilla signing.

The API origin is fixed in the extension source. The checked-in value points to
the documented local deployment (`http://localhost:8080`); set the production
origin in the source and matching Firefox host permission before packaging for
another deployment. See the extension README for details and limitations.

## Tests and checks

Run the backend verification (including integration tests; Docker is required),
frontend tests/build, PWA checks, and Firefox extension tests with:

```
mvn -B -ntp -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
npm --prefix frontend run verify:pwa
npm --prefix web-extension run test:firefox
```

GitHub Actions runs backend verification, frontend lint/tests/build, and the
Firefox extension tests on pushes and pull requests.

## Environment configuration

`.env.example` lists local database values and placeholders for the application
timezone, allowed frontend origin, and Web Push VAPID keys. Set
`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, and `VAPID_SUBJECT` in the process
environment before starting the backend, then set `VAPID_ENABLED=true` to enable
delivery. The public-key endpoint exposes only the public value. Replace the
example database password before any non-local deployment, store real values in
the ignored `.env` file or a secret manager, restrict access to that file, and
never commit `.env` or real VAPID private keys. `.env.example` contains
placeholders only.

Backend integration tests use Testcontainers to start a disposable PostgreSQL
17 container. They require Docker and use isolated schemas; they do not connect
to the Compose database or require existing application data.

## License

Open Link Learn is licensed under the MIT License. See [LICENSE](LICENSE) for the full
text.
