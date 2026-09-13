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

## How We Code

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

### Naming

- Packages are organized by feature (bounded context), not by technical layer:
  `consumer.registration`, `consumer.auth`, `consumer.savings`, and so on. Within a
  feature, `command/` and `query/` subtrees each own their full stack (controllers,
  services, repositories, DTOs, entities); do not import across the two sides.
- Name classes for their behavioral role. Suffixes like `Evaluator`, `Deriver`, and
  `Holder` say what the class does; avoid vague suffixes like `Manager`, `Helper`, or
  `Util`.
- Prefer small value objects over passing bare `String`s for domain concepts.
- Commit messages are imperative and prefixed with the feature area, for example
  `auth: bind device fingerprint to refresh token`.

## Signing your commits

All commits must be signed, and every commit on a pull request must show as
**Verified** on GitHub. A CI check (`Fineract Signed Commits Check`) fails the pull
request otherwise and posts a comment explaining which commits failed, why, and how to
fix them.

For GPG setup instructions, see the
[Fineract GPG Guide](https://fineract.apache.org/docs/current/#_gpg_2).
