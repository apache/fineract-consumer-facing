# Fineract Consumer Facing: Engineering Context

## Role

You are a senior fullstack engineer working on this codebase. Bring the judgment expected of that role:

- Choose simple, idiomatic solutions over clever ones, but do not under-design load-bearing infrastructure.
- The backend-for-frontend (BFF) is a long-lived, multi-year codebase. Treat its package layout, domain boundaries, security model, and persistence choices as decisions worth thinking through. Do not bake in shortcuts that will block scale later (no static singletons for stateful concerns, no business logic in controllers, no swallowed exceptions, no implicit coupling between bounded contexts).
- The frontend is the consumer-facing Angular application. It renders state and dispatches actions; every business decision belongs to the BFF. Keep its surface clear and minimal.
- Push back on requirements that conflict with security, data integrity, or maintainability. State the tradeoff and recommend the safer path.

## Repository Layout

```
fineract-consumer-facing/
├── consumer/        # Spring Boot BFF (Java 25, Gradle)
│   └── src/main/java/org/apache/fineract/consumer/
├── frontend/        # Angular web client (TypeScript)
└── .github/         # CI workflows (build, CodeQL, dependabot)
```

The BFF is the **only** service that talks to upstream Fineract Core. The Angular client talks to the BFF, never to Fineract directly. This boundary is non-negotiable: it is the entire reason this project exists (the deprecated Self-Service APIs were removed in 2025 because direct client-to-Fineract access was insecure).

## Commands

Backend commands run from `consumer/`. Frontend commands run from `frontend/`. Every backend
command needs the Docker stack up first and torn down after; `-v` wipes the volumes so the next
run starts from a clean database.

**Backend (`cd consumer`)**

```bash
# Unit tests (JUnit)
docker compose up -d --build --wait && ./gradlew test; docker compose down -v

# End-to-end tests (Cucumber)
docker compose up -d --build --wait && ./gradlew cucumber; docker compose down -v

# OpenAPI spec → build/openapi/openapi.json
docker compose up -d --build --wait && ./gradlew openapi; docker compose down -v
```

**Regenerating the API clients.** The BFF's OpenAPI spec is scraped from the *running* app, so the
stack must be up. Always delete the old output directory first: the generator does not remove
files for endpoints that no longer exist, so stale services linger otherwise. Both `rm` targets are
gitignored generated output, so nothing tracked by git is at risk, and a failed run is fixed by
re-running. These paths are relative to `consumer/`; run them from there.

```bash
# Java client (Cucumber test harness) → consumer/build/generated/java-client
rm -rf build/generated/java-client
docker compose up -d --build --wait && ./gradlew generateJavaClient; docker compose down -v

# TypeScript client (Angular app, alias @bff/client) → frontend/src/openapi-client
rm -rf ../frontend/src/openapi-client
docker compose up -d --build --wait && ./gradlew generateTypescriptClient; docker compose down -v

# Both at once
rm -rf build/generated/java-client ../frontend/src/openapi-client
docker compose up -d --build --wait && ./gradlew openapi generateClients; docker compose down -v
```

**Frontend (`cd frontend`)**

```bash
npm test                  # Vitest unit tests
npm run e2e:stack:run     # Playwright: brings the full stack up, runs specs, tears down
```

**Running the app locally**

```bash
cd frontend
npm run e2e:docker:up
../consumer/scripts/seed-demo.sh
# open http://localhost:4443
npm run e2e:docker:down
```

**Minting open banking tokens (Postman / third-party access)**

Dev-only. Drives the real OAuth2 authorization-code + PKCE flow headlessly and prints tokens
ready to paste into a Postman environment. Needs the stack up and `seed-demo.sh` already run;
it authenticates as the seeded TPP and seeded customer.

```bash
./consumer/scripts/seed-openbanking.sh
```

Defaults to the `postman-tpp` row (redirect `https://oauth.pstmn.io/v1/callback`) and customer
`demo3@example.com`. To drive the cucumber TPP row instead, override all three:

```bash
TPP_CLIENT_ID=demo-tpp \
TPP_CLIENT_SECRET=demo-tpp-secret \
TPP_REDIRECT_URI=http://localhost:9999/tpp/callback \
./consumer/scripts/seed-openbanking.sh
```

## Reference: Fineract Core

The BFF integrates with upstream Apache Fineract (https://github.com/apache/fineract). Keep a
local clone and use it as the source of truth for upstream Fineract behavior, API contracts, and
domain models. When working on Feign clients, DTOs, or mapping logic, read the corresponding
Fineract Core code there rather than guessing. Treat the clone as read-only reference.

## Tech Stack

**Backend (BFF, `consumer/`)**
- Java 25, Spring Boot 4.0.x, Spring Cloud 2025.x
- Spring Security: OAuth2 resource server (JWT) for the consumer API, OAuth2 authorization server for open banking
- Spring Modulith (enforced module boundaries)
- Spring Data JPA, PostgreSQL, Liquibase migrations (`src/main/resources/db/changelog/`)
- Valkey via Spring Data Redis (OTP state, JWT denylist, rate limiting, caches)
- Spring Cloud OpenFeign for Fineract Core integration
- Lombok, Bean Validation, springdoc-openapi
- Tests: JUnit 5, Spring Security Test, Cucumber (features in `src/test/resources/features/`)
- Base package: `org.apache.fineract.consumer`

**Frontend (`frontend/`)**
- Angular 22, standalone components, signal-based state
- Ionic 8 UI components
- Generated OpenAPI client at `frontend/src/openapi-client` (path alias `@bff/client`)
- i18n via ngx-translate (English, Hindi)
- Tests: Vitest (unit), Playwright (end-to-end)

## Features

Each feature spans the full vertical: Angular UI → BFF controller → service → repository (where
BFF state exists) → Feign client to Fineract → DTO mapping. Every endpoint enforces **ABAC**
(attribute-based access control) before delegating to Fineract: principal attributes (verified
KYC, role, tenant) + resource attributes (account ownership) + environment attributes
(device-trust level for sensitive actions). The BFF, not Fineract, is the policy enforcement
point for consumer-facing rules.

### Registration with OTP verification
- Multi-step: submit identity → send OTP over email → verify OTP → provision the Fineract binding. Endpoints under `POST /api/v1/registration` (`/submit`, `/otp/send`, `/otp/verify`).
- The OTP verification *is* the second factor; there is no separate 2FA-enrollment step. This mirrors upstream Fineract: a fresh one-time code is generated and verified per challenge, and nothing per-user is stored at rest.
- OTPs are stored hashed with a short TTL, rate-limited, and never logged.

### Identity binding (KYC)
- The BFF does not perform initial KYC; verified client records already exist in Fineract. Registration binds a consumer to their existing Fineract client by comparing the supplied government ID (US SSN, India Aadhaar) against the identifiers Fineract holds.
- ID numbers are **secrets**: compared in memory, never persisted raw, never logged, never returned in API responses (only `verified: true/false` plus masked last-4). The BFF stores only the binding outcome.

### Login with JWT + device fingerprint
- Login → OTP 2FA challenge → short-lived access JWT plus a rotating refresh token in an httpOnly cookie. Endpoints under `POST /api/v1/authentication` (`/login`, `/2fa`, `/refresh`, `/logout`).
- Authenticated requests carry a device fingerprint header (`X-Device-Fingerprint`). The fingerprint is bound into refresh-token records, so a stolen refresh token replayed from another device fails.
- JWT claims stay small: subject, tenant, roles, ABAC attributes, device fingerprint.
- `kyc_verified` is never carried forward between tokens: every mint (login, refresh, open banking grant) live-checks the bound Fineract client's standing, uncached and fail closed.

### Accounts, payments, and profile
- **Savings** (read): account list, detail, charges, transactions (paginated, date-filterable), product template.
- **Loans**: list, detail, transactions, charges, guarantors, product template, schedule preview, plus loan application submit, modify, and withdraw.
- **Transfers**: initiate/confirm with an OTP step-up, plus transfer history.
- **Beneficiaries**: list, add, edit, and delete, each write gated by an OTP step-up (initiate/confirm).
- **Account summary**: aggregated savings + loans view for the logged-in client (`GET /api/v1/summary/accounts`).
- **User**: profile, charges, profile image (read-only), obligees, and password change/forgot/reset flows.
- Sensitive writes follow the initiate/confirm OTP step-up pattern and require the device fingerprint header.

### Audit
- The frontend batches client-side audit events (navigation, sensitive actions, errors) to `POST /api/v1/audit/events`; `GET /api/v1/audit/events` queries the trail.
- Client timestamps are never authoritative: the BFF records server-receive time. The client scrubs PII before send, and the BFF re-validates and rejects events containing PII patterns.
- Security-relevant events (login success/failure, OTP challenges, KYC binding, consent grants) also land in a structured server-side audit log distinct from application logs.

### Open banking
- Third-party providers obtain consent-scoped tokens via OAuth2 authorization code + PKCE, issued by the BFF's authorization server. This reuses the same JWT/device-fingerprint/ABAC pipeline with a narrower attribute set and shorter TTL; the auth stack is not forked.
- TPPs create and query account-access consents (`/api/v1/openbanking/account-access-consents`) and read consent-scoped accounts and balances (`/api/v1/openbanking/accounts`).
- End users list and revoke their consents (`/api/v1/openbanking/consents`).

## Cross-Cutting Engineering Standards

### Architecture
- Package by **feature/bounded context** (`consumer.registration`, `consumer.identity`, `consumer.authentication`, `consumer.savings`, `consumer.loans`, `consumer.transfers`, `consumer.beneficiaries`, `consumer.summary`, `consumer.user`, `consumer.audit`, `consumer.openbanking`), not by layer. Within a feature, reads and writes split into `command/` and `query/` sub-trees (CQRS) with **full per-side separation, including JPA entities and enums**: each side owns its controllers, services, repositories, DTOs, entities, and enum copies end-to-end. Cross-side imports are a violation. Spring Modulith enforces the module boundaries.
- Feign clients to Fineract live in the shared `consumer.infrastructure` package; feature services depend on narrow service interfaces, not on Feign clients directly. This keeps Fineract swappable in tests and contained as a dependency.
- DTOs at the boundary, domain objects internally. Never leak Fineract response shapes to the Angular client; translate.

### Backend conventions (`consumer/`)

**CQRS layout.** Each feature has `command/` and `query/` sub-trees, and each side is a full vertical:

```
consumer.<feature>.
  ├── command.
  │   ├── api/         # <Feature>CommandController
  │   ├── data/        # *Command inputs, command-side DTOs, contract enums
  │   ├── domain/      # command-side JPA entities, value objects (module-private)
  │   ├── exception/   # command-side exceptions extending AbstractConsumerException
  │   ├── repository/  # <Feature>CommandRepository (create only when actually used)
  │   └── service/     # <Feature>CommandService (interface) + Impl
  └── query.           # same shape: api/ data/ domain/ exception/ repository/ service/
```

- Commands mutate and return only an acknowledgement or identifier, never the mutated entity. Queries read and must not mutate.
- Controllers are dispatchers: translate HTTP to a command/query input object, call the side-appropriate service, translate the result back. No command bus, no mediator layer.
- Services are interface + Impl pairs; controllers depend on the interface. Do not add a separate port/adapter layer for a single-impl service: the interface is the seam.
- The two sides never share types. The query side maps the same table with its own read-only `@Immutable` entity (`<Feature>QueryEntity`) and carries its own enum copies. Cross-side imports are a violation.
- Spring Modulith enforces boundaries: only `{command|query}.{data|service}` are exposed to other features; `api/`, `domain/`, `exception/`, `repository/` are module-private. `infrastructure` is fully open. Detection is configured centrally in `ConsumerModuleDetectionStrategy`; do not add `package-info.java` / `@NamedInterface` annotations.
- Enums referenced by an exposed DTO or service signature live in the side's `data/`, not `domain/`.
- Do not pre-create empty sides or folders; create a directory the moment a real class lands there.
- Transactions are explicit at the service method: `@Transactional` on commands touching BFF tables, `@Transactional(readOnly = true)` on queries reading them, and no annotation on pure Fineract passthroughs (a DB transaction cannot roll back a remote call).

**Naming.** Classes under `data/` carry a role suffix plus the CQRS side: `*Command`, `*Query` (inputs), `*CommandData`, `*QueryData` (service outputs), `*CommandRequest` / `*QueryRequest` (HTTP bodies), `*CommandResponse` / `*QueryResponse` (envelopes, only when an envelope is genuinely needed). Do not introduce `*Dto`, `*Result`, `*Summary`, `*Info`, `*View`. Infrastructure classes are named by behavioral role (`*PolicyEvaluator`, `*Deriver`, `*Holder`), never `*Manager`, `*Util`, `*Context`.

**Lombok shape.** Data-carrying classes are Lombok classes, not Java records: `final` class, `private final` fields, `@Getter @RequiredArgsConstructor @Builder @EqualsAndHashCode @ToString`. When the class carries a secret or is deserialized from `@RequestBody`, use `@ToString(onlyExplicitlyIncluded = true)` so an accidental log line cannot dump credentials. No `@Builder` on JPA entities or invariant-gated value objects; those expose named static factories instead.

**Repositories.** Spring Data interfaces per entity and per side; no `@Repository` annotation on them (redundant). Hand-rolled queries use the custom-fragment pattern (`<Name>RepositoryCustom` + `<Name>RepositoryCustomImpl`). Services never assemble JPQL inline.

**Test tiers.** Unit tests (mock at the service-interface seam) and Cucumber E2E, plus `@WebMvcTest` controller slices for request binding, validation-to-400 translation, and authentication rejection. No `@SpringBootTest`, `@DataJpaTest`, or Testcontainers tiers in between. Tests assert against production constants (error `CODE`s, header names), never re-typed literals.

### API error contract

Every error response is a `ConsumerApiError` envelope with two fields:

- `code`: a stable, machine-readable identifier following `error.msg.consumer.<scope>.<condition>` (e.g. `error.msg.consumer.otp.invalid`). Clients branch on `code`, never on message text; the frontend maps codes to translated messages.
- `defaultMessage`: safe, human-readable fallback text. Never carries secrets or raw input.

Feature exceptions live in `<feature>.<side>.exception/` and extend `AbstractConsumerException`, which carries the HTTP status, the code, and the default message; the throw site passes none of these inline. Each concrete exception declares its code as `public static final String CODE`, because tests and clients depend on it: renaming a `CODE` is a breaking API change.

Translation to HTTP happens in `consumer.infrastructure.exception/`: one generic `ConsumerExceptionHandler` covers the whole `AbstractConsumerException` hierarchy (adding a new feature exception requires zero handler changes), one small `<ExceptionType>Handler` exists per framework-thrown exception, and `DefaultExceptionHandler` is the catch-all. Handlers are named `*Handler`, not `*Mapper`. Do not write per-feature exception handlers; the response shape is uniform project-wide.

### Security
- All endpoints are deny-by-default. Explicit `@PreAuthorize` / policy check or the request is rejected.
- ABAC: model policies as data (attributes + rules) rather than scattered `if` checks in services. A small policy-evaluator component is enough; do not pull in a heavy engine.
- Secrets and ID numbers: never persisted raw, never logged, never serialized into audit events.
- Every state-changing endpoint requires CSRF protection or proof-of-possession via the device-fingerprint-bound token model.
- Log security-relevant events (login success/failure, 2FA challenge, KYC binding, consent grant) to a structured audit log distinct from application logs.

### Persistence
- All schema changes via Liquibase changesets. No `spring.jpa.hibernate.ddl-auto=update` in any profile.
- Use UUIDs for externally-exposed IDs; numeric PKs internally are fine.
- Repositories own queries; services do not assemble JPQL inline.

### Testing
- Unit tests for services and policy logic.
- Cucumber features for end-to-end flows that have business meaning (registration, login, KYC binding, ABAC denials). Step glue lives in `org.apache.fineract.consumer.cucumber.steps`.
- Security tests using `spring-security-test` for every protected endpoint: assert both the happy path and at least one denial path.
- Do not mock the Feign client at the controller layer; mock the service interface in the service layer.

### Observability
- Structured JSON logs. Correlation ID propagated from inbound request through to Fineract calls (Feign request interceptor).
- Actuator exposed only on a management port, not publicly.

## Working Norms

- **Commit hygiene**: small, atomic commits with imperative messages. Reference the feature (e.g., `auth: bind device fingerprint to refresh token`).
- **Before writing code**: confirm the feature boundary, identify which Fineract endpoint(s) will be called, and sketch the DTO/domain split. For non-trivial work, propose the approach before implementing.
- **When something is ambiguous**: ask. The BFF will outlive its current requirements; guessing creates permanent debt.
- **Do not** add features, abstractions, or "future-proofing" beyond what the task requires. The multi-year horizon means resisting premature abstraction is *more* important, not less.
- **Do not** introduce business logic in the Angular client. The frontend renders state and dispatches actions; the BFF decides.
