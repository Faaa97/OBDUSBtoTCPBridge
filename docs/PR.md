# Pull requests and commit messages

Read this before opening a PR. Releases and their notes are generated from the commits on `main` (see "Builds, CI and releases" in `AGENTS.md`), so the format below matters.

## How PRs are merged

PRs are **squash merged**: the whole PR becomes one commit on `main`, and that commit's message is the **PR title** (the body is the PR description). So:

- **The PR title must follow the format below.** It becomes one line in the release notes.
- Commits inside the PR branch can be informal; they are squashed away. Still keep them small and focused while working.
- One PR = one logical change. If a PR mixes a feature and an unrelated fix, split it, or the release notes will only show one of them.

## Format: Conventional Commits

```
type(scope): short summary

optional body explaining why

optional footer, e.g. BREAKING CHANGE: ...
```

- **type** (required) picks the section in the release notes:

  | type | Section | Use for |
  |---|---|---|
  | `feat` | Features | New user-facing behavior |
  | `fix` | Bug fixes | A bug fix |
  | `perf` | Performance | Faster or lighter, same behavior |
  | `refactor` | Refactoring | Code change with no behavior change |
  | `docs` | Documentation | Docs only |
  | `test` | Tests | Tests only |
  | `build`, `ci` | Build and CI | Build system, dependencies, workflows |
  | `chore` | Chores | Anything else that isn't user-facing |
  | `revert` | Reverts | Reverting an earlier change |

- **scope** (optional) is a short area name such as `api`, `ui` or `auth`. It's shown in bold before the summary. Reuse existing scopes (`git log --oneline` shows them).
- **summary**: imperative, lower case, no trailing period, about 70 characters at most ("add Google login", not "Added Google login.").
- **Breaking change**: add `!` after the type/scope (`feat(api)!: rename /users to /accounts`) or a `BREAKING CHANGE: ...` footer. It's marked **[breaking]** in the notes and bumps the major version (the minor one while below v1.0.0).

Examples:

```
feat(auth): add Google login
fix(ui): stop the button jumping on hover
docs: add setup steps to README
chore(deps): bump vite to 6.1
feat(api)!: rename /users to /accounts
```

Titles that don't follow the format are **silently left out** of the release notes.

## PR description

Becomes the body of the squash commit. Include:

- **Summary**: what changed and why, in a few bullets.
- **Testing**: what you ran and what you saw. Say so if something wasn't tested.
- Any follow-ups or open questions for the user.
