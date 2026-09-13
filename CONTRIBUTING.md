<!--
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
-->

# Contributing

Thanks for contributing. Pull requests target the `main` branch.

Build, run, and test commands are documented in
[docs/consumer/development/build-and-test.adoc](docs/consumer/development/build-and-test.adoc).

## Code Styles

### Code style

**Backend (`consumer/`)** has no automated formatter yet. Match the style of the
surrounding code: the existing indentation, import ordering, Lombok usage, and comment
density of the file you are editing.

**Frontend (`frontend/`)** is formatted with Prettier and linted with ESLint, and CI
fails on violations. Before pushing:

```bash
cd frontend
npm run format      # fix formatting
npm run lint        # check lint rules
```

### Backend structure

- Packages are organized by feature (bounded context), not by technical layer:
  `consumer.registration`, `consumer.authentication`, `consumer.savings`, and so on.
- Each feature splits into a `command/` side (writes) and a `query/` side (reads), and
  each side owns its full stack: `api/`, `data/`, `service/`, plus `domain/`,
  `repository/`, and `exception/` where needed. Create a side only when a real class
  lands there.
- These boundaries are enforced, not just style: Spring Modulith and ArchUnit tests
  fail the build if a class imports across the two sides of a feature. Other features
  may depend only on a side's `data/` and `service/` packages; everything else is
  module-private.

### Naming

- Classes are named `<Feature><Side><Layer>`: `TransfersCommandController`,
  `SavingsQueryService` and `SavingsQueryServiceImpl`,
  `BeneficiaryCommandRepository`. Services are an interface plus `Impl` pair;
  controllers depend on the interface.
- Classes under `data/` carry the CQRS side plus a role suffix: inputs are `*Command`
  and `*Query` (`InitiateTransferCommand`), service outputs are `*CommandData` and
  `*QueryData` (`TransferQueryData`), HTTP request bodies are `*CommandRequest`, and
  response envelopes are `*QueryResponse`.
- Avoid vague suffixes like `Manager`, `Helper`, or `Util`. The suffixes `*Dto`,
  `*Info`, `*Result`, and `*View` are not used either.
- Prefer small value objects over passing bare `String`s for domain concepts.

### Errors

- A new failure case is a small exception class in the feature's `exception/` package
  extending `AbstractConsumerException`. It declares its error code as
  `public static final String CODE = "error.msg.consumer.<scope>.<condition>"` and
  passes the HTTP status, the code, and a safe default message to `super(...)`; the
  throw site passes nothing.
- Do not add per-feature `@RestControllerAdvice` handlers. The shared
  `ConsumerExceptionHandler` picks up the whole hierarchy automatically.

### DTOs and money

- Data-carrying classes are Lombok-annotated `final` classes, never Java records:
  `@Getter @RequiredArgsConstructor @Builder @EqualsAndHashCode @ToString`.
- Request bodies and anything carrying a secret swap `@ToString` for
  `@ToString(onlyExplicitlyIncluded = true)` so credentials cannot leak into logs.
- JPA entities do not get `@Builder`. Construct them through named static factory
  methods that enforce invariants (for example `Beneficiary.register(...)`).
- Money is always `BigDecimal`, never `double` or `float`. Database money columns use
  `precision: 19, scale: 6`.

### API endpoints

Every controller method declares `@Operation(operationId = "...")`. The generated
TypeScript and Java client method names derive from it, so an endpoint without one
breaks client generation.

### Database migrations

- Schema changes are Liquibase YAML changelogs under
  `consumer/src/main/resources/db/changelog/<feature>/`, named
  `NNN-<verb>-<subject>.yaml` where `NNN` is a repo-wide increasing sequence.
- One changeSet per file, with `id` equal to the filename stem,
  `author: fineract-consumer-facing`, and a `logicalFilePath` matching the file's own
  path. Register the file in `db.changelog-master.yaml` in numeric order.

### Frontend

- Components are standalone, signal-based, `ChangeDetectionStrategy.OnPush`, with
  inline templates.
- Feature state lives in an injectable `<feature>.store.ts` exposing signals;
  components inject the store rather than calling APIs directly.
- All API calls go through the generated `@bff/client`; never hand-write HTTP calls.
  Regenerate the client with `npm run generate:client`.
- Reusable styles are partials in `src/app/shared/css/`; component-specific styles
  stay inline in the component.
- Every user-visible string is a translation key resolved from `public/i18n/`;
  `npm run i18n:check` fails CI on hardcoded text.

### License headers

Every source file (Java, TypeScript, YAML, `.feature`, scripts) carries the Apache
license header in the appropriate comment syntax. The Apache RAT CI check fails
without it.

## Before you push

CI gates every pull request on:

- Backend build, unit tests, and Cucumber end-to-end tests
- Frontend unit tests (Vitest) and Playwright end-to-end tests
- Prettier formatting and ESLint (`npm run format:check`, `npm run lint`)
- `npm run i18n:check` (no hardcoded user-facing text)
- Apache RAT license headers and the Category X dependency-license check
- Signed commits (see below)

## Signing your commits

All commits must be signed, and every commit on a pull request must show as
**Verified** on GitHub. A CI check (`Fineract Signed Commits Check`) fails the pull
request otherwise and posts a comment explaining which commits failed, why, and how to
fix them.

For GPG setup instructions, see the
[Fineract GPG Guide](https://fineract.apache.org/docs/current/#_gpg_2).
