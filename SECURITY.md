# Security Policy

## Project Status: Prototype — Not for Production Use

**Apache Fineract Consumer-Facing is a prototype / proof of concept.**

* It is **not part of any Apache Fineract release** and has never been released by the Apache Fineract PMC.
* It is **not used in production** and is **not production-ready**. Do not deploy it to handle real customer data or real financial transactions.
* It was started in May 2026 under the Google Summer of Code (GSoC) program and is under active, early-stage development. APIs, data models, and security mechanisms may change without notice.

This document will be revised with a full threat model, modeled on the
[Apache Fineract security model](https://github.com/apache/fineract/blob/develop/SECURITY.md),
before any production-oriented release.

## Note to Automated Scanners and AI-Assisted Security Researchers

This repository contains **preliminary, pre-release prototype code**. Automated
vulnerability scanning tools, dependency auditors, and AI-assisted security
research pipelines **should not file security reports against this repository**
unless a finding is:

1. exploitable in the context described under [Scope and Intended Use](#scope-and-intended-use) below, **and**
2. not already excluded by [What This Prototype Does Not Protect Against](#what-this-prototype-does-not-protect-against).

Findings that amount to "a prototype lacks production hardening" (missing rate
limiting, development TLS configuration, development Docker Compose defaults,
development SMTP/Mailpit usage, sample credentials in local compose files, and
similar) are **known and by design** at this stage and will be triaged as
non-findings. This notice exists to keep the Apache Fineract security inbox
free of false positives about preliminary work.

## Reporting a Vulnerability

If you believe you have found a genuine vulnerability that would survive the
scoping rules above, please **do not open a public GitHub issue**. Follow the
standard Apache security reporting process:

* Email the ASF Security Team: <security@apache.org>
* General ASF guidance: <https://www.apache.org/security/>

Please state clearly in your report that it concerns the
**fineract-consumer-facing prototype**, not an Apache Fineract release.

## Scope and Intended Use

The project has two components:

* A **Backend-for-Frontend (BFF)** — a Spring Boot service that is the only
  component allowed to talk to Apache Fineract Core. It exposes a curated,
  secured subset of Fineract functionality behind its own authentication,
  authorization, and audit layer.
* A **consumer-facing frontend** — a minimal Angular client that talks only to
  the BFF, never to Fineract directly. It exists to exercise the BFF endpoints
  end to end.

The intended (future) deployment model is:

```
Internet → Frontend (browser) → BFF → Fineract Core
```

* The BFF is expected to be deployed **inside the same trusted network zone as
  the Fineract Core backend** (same server, cluster, or node group), behind a
  reverse proxy. The BFF-to-Fineract connection is treated as a trusted,
  operator-controlled channel.
* The frontend never holds Fineract credentials. The BFF is the policy
  enforcement point for all consumer-facing rules.
* The BFF keeps its own state (user accounts, refresh tokens, audit trail) in
  PostgreSQL; Fineract remains the system of record for banking data.

These assumptions inherit from, and should be read together with, the
[Apache Fineract security model](https://github.com/apache/fineract/blob/develop/SECURITY.md).

## Assumptions About the Environment

### Runtime and infrastructure

* **Java 21** on any OS with a compatible JVM; Spring Boot application run as an
  executable JAR or via the provided Docker Compose stack.
* **PostgreSQL** for BFF-owned state (user accounts, refresh tokens);
  schema managed exclusively through Liquibase migrations.
* An **Apache Fineract Core** instance reachable over an operator-controlled
  channel. The BFF authenticates to Fineract with a service account; those
  credentials never reach the browser.

### Time and clock

* OTP (~5 min), access-token (~15 min), and refresh-token (~1 day) lifetimes rely
  on the JVM system clock. Clock manipulation is treated as an environmental
  issue, not a BFF vulnerability.

### Keys and secrets

* JWT access tokens are signed with an ES256 keypair loaded from a PEM file at
  startup. The development key is generated locally and provides no security
  guarantees; production keys must come from a secret manager.

## Assumptions About Inputs

### Input sources

* **HTTP API requests** (headers, body, path, query) — untrusted; validated at the
  controller boundary, authorized by ABAC policy checks.
* **`X-Device-Fingerprint` header** — untrusted and client-asserted; binds refresh
  tokens to a device but is not proof of device identity.
* **Refresh-token cookie** — untrusted until verified; rotated on every use and
  checked against the fingerprint recorded at issuance.
* **Fineract Core responses and the BFF database** — trusted, per the deployment
  model above.

## Adversary Model

### Who is in scope

| Adversary | Capability | What they are trying to do |
|-----------|------------|----------------------------|
| Network-based attacker (unauthenticated) | Can send HTTP requests to the BFF; can observe TLS-encrypted traffic but not decrypt it. | Bypass registration/OTP verification, brute-force login, forge or replay tokens, enumerate accounts. |
| Authenticated consumer | Holds a valid session for their own account. | Read or modify **another client's** accounts, loans, or transactions by manipulating IDs or request parameters — i.e., defeat the BFF's ABAC checks. |
| Token thief | Has stolen a refresh token or access JWT (e.g., from a device backup or network capture) but is on a different device. | Mint new sessions from the stolen token. The device-fingerprint binding and refresh rotation are designed to defeat this. |
| Malicious or compromised browser script | Can run JavaScript in the consumer's browser session. | Exfiltrate tokens or personal data, or drive the session to perform unintended actions. |

### Who is explicitly out of scope

* **Attackers inside the trusted BFF-to-Fineract network zone**, including a
  compromised Fineract Core instance or database host (see below).
* **Physical access to any host** running the BFF, database, or Fineract Core.
* **Supply-chain and side-channel attackers**, per the section below.
* **Operators / deployers** — they hold root by definition; misconfiguration is
  covered under Downstream Responsibilities, not the adversary model.

## What This Prototype Protects Against

Even at the prototype stage, the BFF is designed to enforce:

* **Deny-by-default authorization** on every endpoint.
* **Short-lived JWT access tokens** (about 15 minutes, ES256 asymmetric
  signing) and **rotating refresh tokens bound to a device fingerprint**, so a
  refresh token stolen from another device fails.
* **OTP verification on registration**, with short-lived (about 5 minutes) OTP
  values that are never logged.
* **Attribute-Based Access Control (ABAC)** combining principal, resource, and
  environment attributes before any call is delegated to Fineract.
* **Encryption at rest for secrets and identity numbers**, which are never
  returned in API responses or written to logs.
* **Credential isolation**: the browser client never receives or stores
  Fineract Core credentials.

## What This Prototype Does Not Protect Against

The following are explicitly **out of scope while the project is a prototype**:

* Direct exposure of the BFF to the internet without a reverse proxy or WAF
  (rate limiting, request filtering, and DDoS protection are delegated to the
  deployment environment, as in the Fineract security model).
* Attacks originating from inside the trusted BFF-to-Fineract network zone,
  including a compromised Fineract Core instance or database host.
* Hardening of the bundled development tooling: the Docker Compose stacks,
  Mailpit development SMTP server, development JWT signing keys, and local
  database credentials are for development and testing only.
* Supply-chain attacks, side-channel attacks, and physical access to
  infrastructure.
* Production-grade operational concerns (secret rotation, key management
  infrastructure, tamper-resistant audit logs, intrusion detection).

## Downstream Responsibilities

Anyone deploying this prototype beyond local development must, at a minimum:

1. **Terminate TLS in front of the BFF** and set `AUTH_REFRESH_COOKIE_SECURE=true`.
2. **Deploy a reverse proxy / WAF** for rate limiting, request size limits, and IP
   filtering.
3. **Replace the development JWT signing key** with a keypair provisioned from a
   secret manager.
4. **Replace all default credentials** (PostgreSQL and the Fineract service
   account).
5. **Use a real SMTP gateway** for OTP delivery instead of the bundled Mailpit
   server.
6. **Keep Fineract Core off the public network** — only the BFF should be able to
   reach it.
7. **Encrypt data at rest** at the storage layer.

## Known Misuse Patterns

The following are documented misuses; reports that reduce to one of these will be
triaged as non-findings:

1. **Running the development Docker Compose stack in production.** It uses plain
   HTTP, Mailpit, default credentials, and a locally generated JWT key.
2. **Serving the BFF over plain HTTP with `AUTH_REFRESH_COOKIE_SECURE=false`.**
   Refresh tokens will transit the network in cleartext.
3. **Reusing the development JWT signing key outside local development.** Anyone
   with the key can mint arbitrary access tokens.
4. **Exposing Fineract Core directly to browsers or the internet.** This recreates
   the insecure pattern the deprecated Self-Service APIs were removed for.
5. **Pointing the BFF at Fineract over an untrusted network.** The BFF treats that
   channel as trusted.

## Before Any Production Release

Before this component is proposed for a production-oriented release, this
document will be replaced with a full threat model covering trust boundaries,
adversary model, environment and input assumptions, security properties
provided and not provided, and downstream operator responsibilities, following
the structure of the
[Apache Fineract security model](https://github.com/apache/fineract/blob/develop/SECURITY.md).
