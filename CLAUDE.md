# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Settly API — Spring Boot 4 / Java 25 backend for a Splitwise-style expense-splitting app (Maven, package `pl.settly.settly_api`). Auth is delegated to Keycloak; data lives in Postgres (Flyway-managed) with Redis for caching.

## Commands

```bash
# Local infrastructure (required before running the app or tests)
docker compose up -d postgres redis keycloak nginx

# Run the app (dev profile is the default; API at http://localhost:8080/api)
./mvnw spring-boot:run

# All tests (needs postgres + redis up — no Testcontainers; @SpringBootTest hits localhost)
./mvnw verify

# Single test class / method
./mvnw test -Dtest=ExpensesServiceTest
./mvnw test -Dtest=ExpensesServiceTest#methodName

# Format (google-java-format via Spotless; the pre-commit hook runs this automatically)
./mvnw spotless:apply
```

- Any Maven invocation triggers an `exec-maven-plugin` step that sets `core.hooksPath=.githooks`. In environments without git (Docker, CI) pass `-Dexec.skip=true`.
- Secrets come from `.env` in the repo root (loaded by springboot3-dotenv); see `.env.example`. Dev profile has working defaults for DB and Keycloak, so `.env` is mostly needed for `GEMINI_API_KEY` / Firebase.
- Local nginx (port 80, host network) fronts everything: `/api/` → app on 8080, `/auth/` → Keycloak on 9090. The dev JWK set URI (`http://localhost/auth/realms/settly/...`) goes through nginx, so keycloak alone isn't enough.

## Architecture

Package-by-feature: `auth`, `projects`, `expenses`, `debts`, `friendships`, `notifications`, `ai`, `common` — each with `controller/service/repository/model/dto` subpackages. DTO mapping uses MapStruct (`*Mapper` interfaces in `dto/`).

**Auth flow** — The app is a stateless OAuth2 resource server; Keycloak (realm `settly`) issues JWTs. `UserSyncFilter` runs after bearer-token auth on every request and upserts a local `users` row from the JWT claims (`UserService.ensureExists`, backed by the Redis `knownUsers` cache so the DB isn't hit per request). Local user IDs equal Keycloak subject UUIDs. `KeycloakAdminService` talks to the Keycloak Admin API via client-credentials (`settly-api-admin` client) for user lookup/search beyond what's in the local DB.

**Domain model** — An `Expense` (with optional `ExpenseItem`s from receipt scanning) is divided via `ExpenseSplit`s (per-user shares; `ExpenseItemSplit` for item-level assignment). Balances are not stored: `DebtService.getBalances` derives net balances live from unsettled splits; settling up marks splits settled and records `Debt` rows. Per-feature `*AccessService` classes (`ExpenseAccessService`, `ProjectAccessService`) centralize ownership/participant authorization checks.

**Settled vs declared** — `ExpenseSplit.settled` is the *owner's* word alone: the creditor confirming money actually arrived. A participant hitting the same settle endpoints does **not** settle — it sets `declaredPaid`/`declaredAt`, an "I paid" *claim* surfaced to the owner as a suggestion to verify and confirm (`ExpensePaymentDeclaredEvent` → owner notification). Declarations never affect balances, are cleared whenever a share becomes settled (owner confirm or settle-up), can be retracted by the participant, and exempt the debtor from the daily settlement reminder (`findDebtorsWithUnsettledShares`). A participant cannot flip an owner-confirmed `settled` back. The endpoints stay the same (`PATCH .../settle`, `/unsettle`); who calls them decides the meaning (`ExpenseSplitService.setSplitSettled`).

**Currency** — An expense records what was spent (`total_amount`/`currency`) *and* what it is worth in the payer's base currency (`base_amount`, at `rate_to_base`, against `base_currency`). The rate is the user's own — what they got when they bought the cash, supplied per expense or inherited from the project — not a looked-up market rate, and it is snapshotted so a rate typed today never restates what a past trip cost. `rate_to_base` is base units per **one** unit of the expense currency (1 GBP = 4.85 PLN → 4.85). A foreign expense with no rate anywhere is refused rather than defaulted to 1: booking GBP 10 as PLN 10 is invisible once it is in the database. `CurrencyConversionService` owns resolution (request → project default → base), rounding, and the apportionment that keeps an expense's shares adding up to exactly its converted total. **Every aggregate sums `base_amount`, never `amount`** — balances, project totals, the settle-up reminder — because adding pounds to zloty yields a number that is not money. A project carries `default_currency`/`default_rate_to_base`: a trip's currency is bought once, not once per expense. A split between two users whose `base_currency` differs is refused (`ExpenseSplitService.requireSharedBaseCurrency`) — their balances could not net, and nothing downstream could detect it.

**Notifications** — Domain services (`ExpenseSplitService`, `FriendshipService`) publish Spring application events; `NotificationEventListener` consumes them `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` and sends FCM pushes through `FcmService`. Firebase is gated by `firebase.enabled` (off by default; credentials are base64 service-account JSON). User-facing notification text is in Polish.

**AI** — `AiGeminiService` uses Spring AI with Gemini (`gemini-2.5-flash`) to extract expense data from receipt photos.

**Persistence** — Schema is managed exclusively by Flyway (`src/main/resources/db/migration`); there is no Hibernate ddl-auto. Add a new `V<n>__*.sql` migration for any schema change. Spring Cache uses Redis with a 1-hour default TTL (`CacheConfig`).

**Tests** — Service tests are plain Mockito unit tests. Controller tests use `@WebMvcTest` and must `@Import(SecurityConfig.class)` plus mock `UserSyncFilter`, `KeycloakJwtAuthenticationConverter`, and `KeycloakUserInfoMapper` (see `ExpensesControllerTest` for the pattern).

## CI/CD

Push to `develop` runs `.github/workflows/ci-cd.yml`: tests against real Postgres/Redis services, then an arm64-only Docker build pushed to GHCR, then deploy on a self-hosted runner via `docker-compose.prod.yml` (Caddy handles TLS). The deployed image is pinned by SHA in `/opt/settly/.env`. `main` is the PR target branch, but deploys track `develop`.
