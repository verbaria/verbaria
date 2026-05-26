# Contributing to Verbaria

Thanks for considering a contribution! This document covers where to file
issues, how to open a pull request, and the conventions we follow for
commits and reviews.


## Issues & feedback

Bugs, feature requests, design discussion — all in
[GitHub Issues](https://github.com/verbaria/verbaria/issues).

There is no external issue tracker.


## Pull requests

The project lives at <https://github.com/verbaria/verbaria>.

1. Fork the repo and create a topic branch off `master`.
2. Make your changes — include tests for anything non-trivial.
3. Make sure the build is green:
   ```bash
   mvn install
   ```
4. Push your branch and open a PR against `verbaria:master`.
5. If reviewers ask for changes, push follow-up commits to the same
   branch — the PR will update automatically.

### Review checklist

- Code passes the existing static checks (the build runs them).
- Behaviour change has a test that fails without it and passes with it.
- Removed code has nothing pointing at it (imports, config keys, docs).
- New user-visible behaviour is documented (README / module READMEs).
- New runtime dependencies are justified — see the README's "retired tech"
  list for what we have already moved away from.

### Branches

- **master** — new features, fixes, refactors. If unsure, target this.
- **release** — only urgent fixes that need to ship out-of-band.


## Commit message format

Loosely follows the Angular convention:

```
<type>(<scope>): <subject>

<body>

<footer>
```

The header (`<type>(<scope>): <subject>`) is mandatory; `<scope>` is
optional.

### Type

- **feat** — new feature
- **fix** — bug fix
- **refactor** — neither feature nor bug fix
- **perf** — performance improvement
- **test** — adding / fixing tests
- **docs** — documentation only
- **style** — whitespace / formatting / no semantic change
- **chore** — build, tooling, dependency updates
- **revert** — reverts an earlier commit (body: `This reverts commit <hash>.`)

### Scope

A component or area: `cli`, `maven-plugin`, `server`, `glossary`,
`translation-memory`, `dependency`, `cleanup` — whatever makes the
change easy to locate.

If the PR closes a GitHub issue, also reference it in the footer:
`Closes #123`.

### Subject

- Imperative, present tense: "add", not "added" / "adds".
- No capital first letter.
- No trailing period.

### Body

Same imperative style as the subject. Explain the *motivation* and how
the new behaviour differs from the old.

### Footer

- `BREAKING CHANGE: …` for any incompatible change (separated from the
  body by a blank line).
- `Closes #N` / `Fixes #N` to link GitHub issues.

Keep any single line ≤ 100 chars so it reads well in `git log` and on
GitHub.


## Developer setup

See the root [README.md](README.md) for prerequisites (JDK 21, Maven 3.9+,
PostgreSQL when running the server) and the build / run instructions.


## After your PR is merged

Delete your topic branch. Pull `master` from upstream so your next branch
starts from the merged change.

Thanks!
