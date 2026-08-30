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

# Apache Fineract Consumer-Facing

[![Backend Build](https://github.com/apache/fineract-consumer-facing/actions/workflows/build-tests-backend.yml/badge.svg?branch=main)](https://github.com/apache/fineract-consumer-facing/actions/workflows/build-tests-backend.yml)
[![Frontend Build](https://github.com/apache/fineract-consumer-facing/actions/workflows/build-tests-frontend.yml/badge.svg?branch=main)](https://github.com/apache/fineract-consumer-facing/actions/workflows/build-tests-frontend.yml)
[![CodeQL](https://github.com/apache/fineract-consumer-facing/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/apache/fineract-consumer-facing/actions/workflows/codeql.yml)

Apache Fineract Consumer-Facing gives end users secure, authenticated access to [Apache Fineract](https://github.com/apache/fineract) core banking functionality. It replaces the Self-Service APIs that were deprecated and removed from Fineract in 2025, which let client applications talk to Fineract Core directly.

The project was started in May 2026 as part of the Google Summer of Code (GSoC) program. It has two components:

* A **Backend-for-Frontend (BFF)**: a Spring Boot service that is the only component allowed to talk to Fineract Core. It surfaces a curated, secured subset of Fineract functionality behind its own authentication, authorization, and audit layer.
* A **consumer-facing frontend**: a minimal Angular client that talks only to the BFF, never to Fineract directly. It exists to exercise the BFF endpoints end to end.

The client to BFF to Fineract boundary is the reason the project exists: the frontend never holds Fineract credentials, and the BFF is the policy enforcement point for all consumer-facing rules. The BFF keeps its own state (user accounts, refresh tokens, audit trail) in PostgreSQL; Fineract remains the system of record for banking data.


Requirements
============
* Java >= 21 (Azul Zulu JVM is tested by upstream Fineract CI on GitHub Actions)
* Node.js >= 22 and npm >= 10 (for the frontend)
* PostgreSQL >= 18 (the BFF keeps its own database, separate from Fineract's)
* Docker and Docker Compose (the bundled compose stack runs the BFF database, Fineract Core, a development SMTP server, and the BFF)

A running Apache Fineract Core instance is required for the BFF to call. The bundled Docker Compose stack provides one.


Security
============
The project exists because the previous direct-to-Fineract pattern was insecure. The BFF enforces:

* Deny-by-default authorization on every endpoint.
* Short-lived JWT access tokens (about 15 minutes) and rotating refresh tokens bound to a device fingerprint, so a refresh token stolen from another device fails.
* OTP verification on registration. OTP values are short-lived (about 5 minutes) and never logged.
* Attribute-Based Access Control (ABAC) combining principal, resource, and environment attributes before any call is delegated to Fineract.

Secrets and identity numbers are encrypted at rest and are never returned in API responses or written to logs.


Project Layout
============

The BFF is organized by feature and bounded context, not by layer. Feature services depend on narrow port interfaces; the Fineract Feign client is contained in the shared `infrastructure` package so Fineract stays swappable in tests.

```
fineract-consumer-facing/
├── consumer/            Spring Boot BFF (Java 21, Gradle)
│   └── src/main/java/org/apache/fineract/consumer/
│       ├── registration/
│       ├── authentication/
│       ├── otp/
│       ├── savings/
│       ├── loans/
│       ├── transfers/
│       ├── user/
│       ├── audit/
│       └── infrastructure/
├── consumer/
│   └── compose.yaml      backend stack (BFF, databases, Fineract, Mailpit)
├── frontend/            Angular demo client (TypeScript) 
├── docker-compose.e2e.yml     full stack including frontend, for E2E tests
└── .github/workflows/         CI: backend and frontend builds, CodeQL, RAT
```


Instructions
============


How to run the backend
---

The fastest way to bring up the whole backend stack (BFF database, Fineract Core, a development SMTP server, and the BFF) is Docker Compose.

```bash
# get code
git clone https://github.com/apache/fineract-consumer-facing.git
cd fineract-consumer-facing

# jwt local dev signing key generation
./consumer/scripts/generate-dev-jwt-key.sh

# start the backend stack
docker compose -f consumer/compose.yaml up -d
```

The BFF requires a JWT signing key (public/private) for asymmetrical signing with ES256. The `bff` service mounts one at `/etc/bff/jwt-key.pem`; for local Gradle runs the key location defaults to `dev-jwt-key.pem`. 
JWT Signing Key is generated automatically in CI and can be generated locally by invoking:

```bash

```

Once the stack is healthy, the services are available at:

* Consumer BFF: `http://localhost:8080`
* BFF API docs (Swagger UI): `http://localhost:8080/swagger-ui.html`
* Fineract Core: `http://localhost:8888/fineract-provider`
* Mailpit (development OTP inbox): `http://localhost:8025`
* BFF PostgreSQL: `localhost:5432`
* Fineract PostgreSQL: `localhost:5433`


How to run the frontend
---

```bash
cd frontend
npm install
npm start
```

The Angular development server runs on `http://localhost:4200` and talks to the BFF.


How to run for local development
---

Run the entire e2e stack:

```bash
# start full e2e stack
cd frontend
npm run e2e:docker:up
```

The BFF reads configuration from environment variables with local defaults defined in `consumer/src/main/resources/application.properties`. The most relevant are:

* `SPRING_DATASOURCE_URL` (default `jdbc:postgresql://localhost:5432/consumerapp`) — the BFF database.
* `FINERACT_BASE_URL` (default `http://localhost:8888/fineract-provider/api`) — the Fineract Core API.
* `FINERACT_SERVICE_USERNAME` / `FINERACT_SERVICE_PASSWORD` (default `mifos` / `password`) — the BFF service account on Fineract.
* `JWT_KEY_LOCATION` (default `file:dev-jwt-key.pem`) — the JWT signing key.
* `AUTH_ACCESS_TOKEN_TTL` (default `15m`) and `AUTH_REFRESH_TOKEN_TTL` (default `1d`) — token lifetimes.


Testing
============

The backend uses JUnit 5 and Cucumber for end-to-end flows that have business meaning, such as registration, login, and ABAC denials.

Unit testing:

```bash
cd consumer
./gradlew test
```

E2E testing:

```bash
cd consumer
./gradlew cucumber
```

The frontend uses Vitest for unit tests and Playwright for end-to-end tests. The end-to-end suite runs against the full containerized stack defined in `docker-compose.e2e.yml`, which adds the frontend on top of the backend compose stack.

```bash
cd frontend
npm test                # unit tests
npm run e2e:stack:run   # bring up the stack, run Playwright, tear it down
```


Platform API
============

The BFF exposes a versioned REST API under `/api/v1`. When the BFF is running, the Swagger documentation can be accessed at `http://localhost:8080/swagger-ui.html`. 

The openapi spec can be generated by: 
```bash
cd consumer
./gradlew openapi
```

Openapi with Springdoc can be used to generate Typescript and Java Feign clients to target the BFF, or Java Feign clients to target Fineract:

BFF Java clients can be generated:
```bash
cd consumer
./gradlew generateJavaClient
```

BFF Typescript clients can be generated:
```bash
cd consumer
./gradlew generateTypescriptClient
```

Fineract clients can be generated:
```bash
cd consumer
./gradlew generateFineractClient
```



Community
============

This project is part of the Apache Fineract community. If you are interested in contributing, [join the developer mailing list](http://fineract.apache.org/#contribute) or the [Fineract Slack channel](https://app.slack.com/client/T0F5GHE8Y/C028634A61L). Issues are tracked on the [Apache Fineract JIRA](https://issues.apache.org/jira/secure/Dashboard.jspa?selectPageId=12335824).


License
============

This project is licensed under the [Apache License, Version 2.0](LICENSE).
