# Quickstart: Notes Management API

**Phase 1 output** for [plan.md](plan.md). A runnable validation guide proving the feature works end-to-end. Implementation details (model/service/controller code, migrations) live in `tasks.md` and the implementation phase, not here.

## Prerequisites

- Java 21 (JDK)
- Docker and Docker Compose
- `jq` and `curl`
- No manually-obtained token is needed — the Keycloak container is provisioned with a declarative realm import (`docker/keycloak/quicknote-realm.json`) defining the `quicknote` realm, the `notes-api` client, the `user` realm role, and at least one local test user with that role already assigned (research.md §13). Tokens are obtained from Keycloak directly, as shown in step 4.

## 1. Start local infrastructure

```bash
docker compose up -d
```

Provisions PostgreSQL and Keycloak (pre-loaded with the `quicknote` realm import) per `docker-compose.yml` (research.md §13). The application container (once built) connects to both using externalized configuration (constitution: no hard-coded connection strings or issuer URLs).

## 2. Run the application

```bash
./mvnw quarkus:dev
```

Quarkus dev mode applies Flyway migrations automatically on startup (research.md §4) and configures `quarkus-oidc` against the local Keycloak realm's issuer URL.

## 3. Verify operational readiness

```bash
curl -sf http://localhost:8080/q/health/ready
curl -sf http://localhost:8080/q/health/live
```

Both MUST return HTTP 200 with `"status": "UP"` — validates constitution Principle V (Observability & Operational Readiness) before any functional testing.

## 4. Obtain a token from Keycloak and validate core scenarios

Get a token for the pre-provisioned test user via Keycloak's token endpoint (direct grant, local dev only):

```bash
export TOKEN=$(curl -sf -X POST \
  http://localhost:8180/realms/quicknote/protocol/openid-connect/token \
  -d 'grant_type=password' -d 'client_id=notes-api' \
  -d 'username=test-user' -d 'password=<test-user-password-from-realm-import>' \
  | jq -r .access_token)
BASE=http://localhost:8080/api/v1
```

**Expected**: `$TOKEN` is a non-empty JWT whose decoded payload includes `"realm_access": {"roles": [..., "user", ...]}`.

### Capture a note (User Story 1 — P1)

```bash
curl -sf -X POST "$BASE/notes" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title": "Groceries", "content": "Milk, eggs, bread"}'
```

**Expected**: `201 Created`, response body is a `Note` (per `contracts/openapi.yaml`) with the given title/content, `pinned/archived/trashed = false`, and a generated `id`.

```bash
curl -sf "$BASE/notes" -H "Authorization: Bearer $TOKEN"
```

**Expected**: `200 OK`, the created note appears in `content[]` of the paginated response.

### Reject an empty note (Edge Case)

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/notes" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{}'
```

**Expected**: `400` — confirms FR-001's empty-note rejection.

### Organize and prioritize (User Story 2 — P2)

```bash
NOTE_ID=<id from the create response above>
curl -sf -X POST "$BASE/notes/$NOTE_ID/pin" -H "Authorization: Bearer $TOKEN"
curl -sf -X PATCH "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"color": "YELLOW"}'
```

**Expected**: Both return `200` with `pinned: true` and `color: "YELLOW"` respectively, persisted on subsequent `GET`.

### Archive and trash lifecycle (User Story 3 — P2)

```bash
curl -sf -X POST "$BASE/notes/$NOTE_ID/archive" -H "Authorization: Bearer $TOKEN"
curl -sf "$BASE/notes?view=active" -H "Authorization: Bearer $TOKEN"   # note absent
curl -sf "$BASE/notes?view=archived" -H "Authorization: Bearer $TOKEN" # note present

curl -sf -X DELETE "$BASE/notes/$NOTE_ID/archive" -H "Authorization: Bearer $TOKEN" # unarchive
curl -sf -X POST "$BASE/notes/$NOTE_ID/trash" -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/notes/$NOTE_ID/archive" \
  -H "Authorization: Bearer $TOKEN"   # expect 409 — cannot archive from trash (FR-010)

curl -sf -X DELETE "$BASE/notes/$NOTE_ID/trash" -H "Authorization: Bearer $TOKEN" # restore
curl -sf -X POST "$BASE/notes/$NOTE_ID/trash" -H "Authorization: Bearer $TOKEN"
curl -sf -X DELETE "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN"        # permanent delete
curl -s -o /dev/null -w "%{http_code}\n" "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN"
```

**Expected**: The final `GET` returns `404` — confirms FR-013 (permanently deleted notes are unretrievable).

### Labels (User Story 4 — P3)

```bash
LABEL_ID=$(curl -sf -X POST "$BASE/labels" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"name": "Work"}' | jq -r .id)
NOTE_ID=$(curl -sf -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"title": "Q3 planning"}' | jq -r .id)

curl -sf -X PUT "$BASE/notes/$NOTE_ID/labels/$LABEL_ID" -H "Authorization: Bearer $TOKEN"
curl -sf "$BASE/notes?label=$LABEL_ID" -H "Authorization: Bearer $TOKEN"   # note appears
curl -sf -X DELETE "$BASE/labels/$LABEL_ID" -H "Authorization: Bearer $TOKEN"
curl -sf "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN"          # note still exists, label list empty
```

**Expected**: Deleting the label removes it from the note (empty `labelIds`) without deleting the note itself (FR-018).

### Checklist notes (User Story 5 — P3)

```bash
NOTE_ID=$(curl -sf -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"title": "Packing list", "items": [{"text": "Passport"}]}' | jq -r .id)

curl -sf -X POST "$BASE/notes/$NOTE_ID/items" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"text": "Charger"}'
ITEM_ID=$(curl -sf "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" | jq -r '.items[0].id')
curl -sf -X PATCH "$BASE/notes/$NOTE_ID/items/$ITEM_ID" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"checked": true}'
```

**Expected**: The note has two items; the first item's `checked` becomes `true` and persists on the next `GET`.

### Search (User Story 6 — P4)

```bash
curl -sf "$BASE/notes?search=Packing" -H "Authorization: Bearer $TOKEN"     # matches the note's title
curl -sf "$BASE/notes?search=Nonexistent" -H "Authorization: Bearer $TOKEN" # empty content[]
```

**Expected**: the first returns the "Packing list" note; the second returns an empty `content[]`.

> Search covers a note's **title and text content only** (FR-026). Checklist item
> text is deliberately not indexed, so searching for `Passport` above returns
> nothing even though an item with that text exists. If searching within
> checklist items is wanted, that is a change to FR-026, not a defect.

### Cross-user isolation (Security — constitution Principle II, FR-015)

Using a second token (`TOKEN_2`) for a different pre-provisioned user (also holding the `user` role):

```bash
curl -s -o /dev/null -w "%{http_code}\n" "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN_2"
```

**Expected**: `404` (not `403`) — confirms FR-015's not-found-not-forbidden requirement: a valid, correctly-roled caller still gets 404 for a resource they don't own.

### RBAC enforcement (Security — research.md §1b)

Using a token for a user that exists in Keycloak but does **not** have the `user` realm role assigned (or an intentionally malformed/expired token):

```bash
curl -s -o /dev/null -w "%{http_code}\n" "$BASE/notes" -H "Authorization: Bearer $TOKEN_NO_ROLE"
curl -s -o /dev/null -w "%{http_code}\n" "$BASE/notes"   # no Authorization header at all
```

**Expected**: The first returns `403` (valid token, missing role) and the second returns `401` (no/invalid token) — confirms these are deliberately distinct failure modes from the `404` above.

## 5. Run the automated suite

```bash
./mvnw test    # contract tests (REST Assured) — written and confirmed failing before implementation, per mandatory TDD
./mvnw verify  # + integration tests against Dev Services PostgreSQL and Keycloak
```

**Expected**: All tests pass — per constitution Principle III and the mandatory TDD process (plan.md, "Development Process"), this MUST be green before any pull request merges, and no implementation task should exist in `tasks.md` without a test task that preceded it and was observed to fail first.

## Notes

- Full request/response schemas: [contracts/openapi.yaml](contracts/openapi.yaml).
- Full entity/field definitions: [data-model.md](data-model.md).
- TLS termination and disk-level encryption-at-rest are deployment/infrastructure concerns for whatever environment this container runs in (e.g., an ingress/load balancer terminating TLS, encrypted storage volumes) — they are intentionally not part of this application-level quickstart, consistent with the Technical Context constraints in `plan.md`.
- The realm import's test users/passwords are for local development only and MUST NOT be reused in any shared or production Keycloak instance.
