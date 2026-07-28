# Notes Management API

A REST API for a personal note-taking service in the spirit of Google Keep. An
authenticated user can capture notes (plain text or checklists), organise them
with colours, pins and labels, archive or trash them, and search across them.

Built with Java 21 and Quarkus, backed by PostgreSQL, with authentication and
role-based access control delegated to Keycloak.

## Quick start

```bash
docker compose up -d      # PostgreSQL + Keycloak (realm imported automatically)
./mvnw quarkus:dev        # or: mvn quarkus:dev
```

In dev and test mode you do not need `docker compose` at all — Quarkus Dev
Services starts a PostgreSQL and a Keycloak container automatically, importing
the same realm file.

For the full walkthrough — obtaining a token, exercising every user story, and
the expected responses — see
[specs/001-notes-api/quickstart.md](specs/001-notes-api/quickstart.md).

## Running the tests

```bash
mvn test      # contract tests
mvn verify    # + integration tests and formatting check
```

Tests run against a real PostgreSQL and a real Keycloak container rather than
in-memory or mocked substitutes, so the schema, the full-text search and the
token/role configuration are all genuinely exercised.

## API

All endpoints live under `/api/v1` and require a Keycloak-issued bearer token
carrying the `user` realm role.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/notes` | Create a note (text or checklist) |
| `GET` | `/notes` | List notes; supports `view`, `label`, `search`, `page`, `size` |
| `GET` | `/notes/{id}` | Read one note |
| `PATCH` | `/notes/{id}` | Update title, content or colour |
| `DELETE` | `/notes/{id}` | Permanently delete (trashed notes only) |
| `POST`/`DELETE` | `/notes/{id}/pin` | Pin / unpin |
| `POST`/`DELETE` | `/notes/{id}/archive` | Archive / unarchive |
| `POST`/`DELETE` | `/notes/{id}/trash` | Trash / restore |
| `POST` | `/notes/{id}/items` | Add a checklist item |
| `PATCH`/`DELETE` | `/notes/{id}/items/{itemId}` | Update / remove an item |
| `PUT`/`DELETE` | `/notes/{id}/labels/{labelId}` | Apply / remove a label |
| `GET`/`POST` | `/labels` | List / create labels |
| `PATCH`/`DELETE` | `/labels/{id}` | Rename / delete a label |

The authoritative contract is
[specs/001-notes-api/contracts/openapi.yaml](specs/001-notes-api/contracts/openapi.yaml).

Operational endpoints: `/q/health/live`, `/q/health/ready`, `/q/metrics`.

### Status codes worth knowing

- **401** — no token, or the token is invalid or expired.
- **403** — the token is valid but lacks the `user` realm role.
- **404** — the resource does not exist *for you*. A note belonging to another
  user reports 404, never 403, so responses cannot be used to probe for other
  people's data.

Errors always use one envelope:

```json
{ "error": { "code": "NOT_FOUND", "message": "Note not found" } }
```

## Configuration

Nothing environment-specific is hard-coded. In production every value comes from
the environment:

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `OIDC_AUTH_SERVER_URL` | Keycloak realm issuer URL |
| `OIDC_CLIENT_ID` | OIDC client id (default `notes-api`) |
| `TRASH_RETENTION_DAYS` | Days a note stays restorable in the trash (default 7) |
| `TRASH_PURGE_CRON` | When the purge job runs (default 03:00 daily) |

TLS termination and encryption at rest are deployment concerns handled by the
surrounding infrastructure, not by this service.

## Project layout

```text
src/main/java/com/quicknote/api/
├── note/        notes, checklist items, trash purge scheduler
├── label/       labels
├── checklist/   checklist item entity
├── security/    current-user resolution from the validated token
└── common/      error envelope, pagination, correlation id
src/main/resources/db/migration/   Flyway migrations (the only source of schema)
docker/keycloak/                   realm import used by compose and by tests
```

Each feature package separates the JAX-RS resource (HTTP), the service
(business rules and ownership) and the repository (data access).

## Development notes

- The database schema is owned entirely by Flyway migrations; Hibernate never
  generates or updates it.
- Every query is scoped by owner in the repository layer, so there is no code
  path that reads another user's row.
- This project follows the engineering rules in
  [.specify/memory/constitution.md](.specify/memory/constitution.md), and was
  built test-first against the specification in [specs/001-notes-api/](specs/001-notes-api/).
