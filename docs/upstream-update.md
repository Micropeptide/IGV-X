# IGV-X — Upstream Update Workflow

How IGV-X tracks upstream IGV (`igvteam/IGV`) and merges new upstream
releases safely. **Mandatory procedure** — never replace the source tree
with a fresh checkout.

## 1. Repository layout

- `upstream` remote → `https://github.com/igvteam/IGV.git`
- `main` → tracks `upstream/main` (3.0-dev) — informational; IGV-X does
  not build on 3.0 yet.
- `IGV-X` → **the dev base branch**, currently on upstream `2.19.X`
  stable (matches Runtian's IGV 2.19.5 install and the charter's target
  codebase, `org.broad.igv.*`). All IGV-X commits land here.
- Feature branches for larger work; tagged releases for packaging.

## 2. When to update

On explicit request ("Update IGV-X to the latest IGV version") or a
scheduled check. Upstream releases appear as tags/stable branches (e.g.
`2.19.5`, `2.19.6`, …); we track the current stable `2.19.X` line until
the 3.0 migration is deliberately planned.

## 3. The procedure (mandatory)

1. **Backup + record state.**
   `git status --porcelain` clean, then tag/record the current `IGV-X`
   HEAD; `git fetch upstream` first.
2. **Inspect upstream.** `git log upstream/2.19.X --oneline -20` (or the
   new tag); read the upstream release notes; note changes touching files
   we modified (`src/main/java/org/broad/igv/bbfile/*`, sam, feature,
   ui, session).
3. **Diff before merge.** `git diff IGV-X upstream/2.19.X --stat` to see
   the delta; identify likely conflicts with our commits.
4. **Deliberate merge.** On `IGV-X`: `git merge upstream/2.19.X` (or
   rebase only when the history is linear and conflicts are trivial).
   Resolve conflicts **by hand** — never `-X theirs` / `-X ours` blindly:
   our chromosome-resolution and NPE fixes must be preserved; upstream's
   changes win for things we did not touch.
5. **Reapply features if needed.** If a merge dropped or mis-merged any
   IGV-X change (verify with the machine-readable patch list — see §4),
   reapply deliberately and commit.
6. **Full regression suite.** `./gradlew test` green. Run
   `scripts/verify_test_files.py` if real data is present. Update
   `docs/whats-different.md`, `docs/fork-diff.md`, `CHANGELOG.md`.
7. **Update report.** Write `docs/upstream-update-<version>.md`:
   upstream commits pulled, conflicts encountered + resolution, IGV-X
   features re-verified, regression results, remaining risks.
8. **Promote only if IGV-X functionality passes.** If anything regressed,
   fix before promoting; do not ship a half-merged tree.

## 4. Machine-readable patch list

`docs/fork-diff.md` maintains a list of IGV-X patches (commit hashes,
files, one-line description). Before any upstream merge, this list is the
checklist of what must survive; after the merge, verify each entry still
applies (`git log --oneline -- <file>`). The list is also used to
regenerate a clean patch series if we ever need to rebase onto a
reorganized upstream.

## 5. Safety rules

- Never `rm -rf` / re-clone over the working tree; the tree is the fork.
- Never commit a merge without resolving conflicts deliberately and
  running the full test suite.
- Never change the base branch to a different upstream line without a
  written plan (3.0 migration is its own project).
- Every merge is a commit with a message naming the upstream ref merged.
- If a merge produces a broken build, the fix lands as a *separate*
  commit with a clear message, so bisect stays clean.
