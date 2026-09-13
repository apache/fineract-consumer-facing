#!/usr/bin/env python3

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""Fails a PR whose commits GitHub does not report as Verified, and explains why in a PR comment."""

import json
import os
import re
import sys
import urllib.error
import urllib.request

MARKER = "<!-- signed-commits -->"
MAX_COMMITS = 250
CONTRIBUTING = (
    "https://github.com/apache/fineract-consumer-facing"
    "/blob/main/CONTRIBUTING.md#signing-your-commits"
)

REASONS = {
    "unsigned": {
        "summary": "These commits carry no signature at all.",
        "fix": (
            "Set up commit signing, then re-sign the commits already on this branch. "
            f"[CONTRIBUTING.md]({CONTRIBUTING}) has the setup."
        ),
    },
    "no_user": {
        "summary": (
            "These commits **are signed**, but the committer email is not an address "
            "GitHub can match to any account, so it never gets as far as checking the "
            "key. Note that the commits are not attributed to your GitHub profile "
            "either, which is the same cause."
        ),
        "fix": (
            "This is an identity problem, not a signing one: your signing setup is "
            "fine. Check `git config user.email` and set it to an address that is "
            "**verified on your GitHub account** (Settings → Emails), then re-sign."
        ),
    },
    "unverified_email": {
        "summary": (
            "The committer email is on your GitHub account, but that address has not "
            "been verified."
        ),
        "fix": (
            "Verify the address under Settings → Emails, then re-sign. No change to "
            "your signing key is needed."
        ),
    },
    "unknown_key": {
        "summary": "The signing key is not registered on your GitHub account.",
        "fix": (
            "Add the public half of your signing key under Settings → SSH and GPG "
            "keys, as a **signing** key, then re-sign."
        ),
    },
    "unknown_signature_type": {
        "summary": "GitHub does not recognise this signature type.",
        "fix": f"Sign with GPG or SSH; see [CONTRIBUTING.md]({CONTRIBUTING}).",
    },
    "expired_key": {
        "summary": "The signing key has expired.",
        "fix": (
            "Extend or replace the key, upload the new public key to GitHub, then "
            "re-sign."
        ),
    },
    "not_signing_key": {
        "summary": "The key is on your account, but not marked as a signing key.",
        "fix": "Add it under Settings → SSH and GPG keys as a signing key, then re-sign.",
    },
    "bad_signature": {
        "summary": "The signature does not match the commit contents.",
        "fix": "Re-sign the commits.",
    },
    "malformed_signature": {
        "summary": "The signature could not be parsed.",
        "fix": "Re-sign the commits.",
    },
}

FALLBACK = {
    "summary": "GitHub could not verify the signature on these commits.",
    "fix": f"See [CONTRIBUTING.md]({CONTRIBUTING}) for the expected signing setup.",
}


def api(method, path, body=None):
    base = os.environ.get("GITHUB_API_URL", "https://api.github.com")
    request = urllib.request.Request(
        f"{base}{path}",
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={
            "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(request) as response:
        raw = response.read()
    return json.loads(raw) if raw else None


def paginate(path):
    items = []
    page = 1
    while True:
        separator = "&" if "?" in path else "?"
        batch = api("GET", f"{path}{separator}per_page=100&page={page}")
        items.extend(batch)
        if len(batch) < 100:
            return items
        page += 1


def defuse_references(text):
    text = re.sub(r"@(?=[A-Za-z0-9])", "@​", text)
    return re.sub(r"#(?=\d)", "#​", text)


def subject_of(commit):
    subject = (commit.get("commit", {}).get("message") or "").split("\n", 1)[0]
    clipped = subject[:71] + "…" if len(subject) > 72 else subject
    return defuse_references(clipped)


def reason_of(commit):
    return commit.get("commit", {}).get("verification", {}).get("reason") or "unknown"


def group_by_reason(unverified):
    groups = {}
    for commit in unverified:
        groups.setdefault(reason_of(commit), []).append(commit)
    return groups


def build_body(unverified, total, truncated):
    groups = group_by_reason(unverified)
    plural = "commit is" if len(unverified) == 1 else "commits are"

    sections = []
    for reason, commits in groups.items():
        meaning = REASONS.get(reason, FALLBACK)
        listing = "\n".join(
            f"- `{commit['sha'][:8]}` {subject_of(commit)}" for commit in commits
        )
        sections.append(
            f"### `{reason}`\n\n{meaning['summary']}\n\n{listing}\n\n"
            f"**How to fix:** {meaning['fix']}"
        )

    resign = "\n".join(
        [
            "```bash",
            "# only for the identity reasons above (no_user / unverified_email):",
            'git config user.email "you@example.com"',
            "",
            "# re-sign every commit on this branch:",
            "git rebase --exec 'git commit --amend --no-edit --reset-author -S' origin/main",
            "git push --force-with-lease",
            "```",
        ]
    )

    lines = [
        MARKER,
        "",
        "## Commits on this pull request are not verified",
        "",
        f"{len(unverified)} of {total} {plural} not showing as **Verified** on GitHub, "
        "so this pull request cannot be merged into `main` yet.",
    ]
    if truncated:
        lines.append(
            f"\n> This pull request has more than {MAX_COMMITS} commits; only the "
            f"first {MAX_COMMITS} were checked.\n"
        )
    lines.append("")
    for section in sections:
        lines.extend([section, ""])
    lines.extend(
        [
            "### Re-signing",
            "",
            resign,
            "",
            "Force-pushing is expected here: re-signing rewrites the commits, so their "
            "hashes change.",
            "",
            "---",
            "",
            "This comment is posted automatically and updates itself when you push; it "
            "disappears once every commit verifies. If you believe this is wrong, say "
            "so on the pull request; a maintainer can check.",
        ]
    )
    return "\n".join(lines)


def sync_comment(repo, pr_number, body):
    comments = paginate(f"/repos/{repo}/issues/{pr_number}/comments")
    existing = next(
        (
            comment
            for comment in comments
            if (comment.get("user") or {}).get("type") == "Bot"
            and MARKER in (comment.get("body") or "")
        ),
        None,
    )

    if body is None:
        if existing:
            api("DELETE", f"/repos/{repo}/issues/comments/{existing['id']}")
            return "deleted"
        return "noop"

    if existing:
        api("PATCH", f"/repos/{repo}/issues/comments/{existing['id']}", {"body": body})
        return "updated"
    api("POST", f"/repos/{repo}/issues/{pr_number}/comments", {"body": body})
    return "created"


def main():
    repo = os.environ["GITHUB_REPOSITORY"]
    pr_number = int(os.environ["PR_NUMBER"])

    commits = paginate(f"/repos/{repo}/pulls/{pr_number}/commits")
    checked = commits[:MAX_COMMITS]
    truncated = len(commits) > MAX_COMMITS
    unverified = [
        commit
        for commit in checked
        if commit.get("commit", {}).get("verification", {}).get("verified") is not True
    ]

    for commit in checked:
        verified = commit.get("commit", {}).get("verification", {}).get("verified")
        icon = "✅" if verified else "❌"
        print(f"{icon} {commit['sha'][:8]} {reason_of(commit)}")

    if not unverified:
        action = sync_comment(repo, pr_number, None)
        print(f"All {len(checked)} commit(s) verified. Comment: {action}.")
        return 0

    for commit in unverified:
        print(
            f"::error title=Unverified commit::Commit {commit['sha'][:8]} is not "
            f"verified by GitHub ({reason_of(commit)})."
        )

    body = build_body(unverified, len(checked), truncated)
    action = sync_comment(repo, pr_number, body)
    print(f"Comment {action}.")

    print(
        f"::error::{len(unverified)} of {len(checked)} commit(s) are not verified by "
        "GitHub. See the comment on the pull request."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
