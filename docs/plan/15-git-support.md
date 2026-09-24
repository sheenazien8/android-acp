# Git Support (Git tab in the chat sidebar)

## Context
- files:
  - @docs/plan/14-file-manager.md (extension protocol, `WorkspaceApi`, sidebar, `bridgeFeatures`)
  - @app/src/main/java/com/lakasir/acp/acp/AcpMethods.kt (`LakasirMethods`, `LakasirErrorCode`)
  - @app/src/main/java/com/lakasir/acp/acp/WorkspaceApi.kt
  - @app/src/main/java/com/lakasir/acp/data/repository/WorkspaceRepository.kt
  - @app/src/main/java/com/lakasir/acp/ui/workspace/ (sidebar, Files tab)
  - @app/src/main/java/com/lakasir/acp/ui/chat/LineDiff.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/components/DiffBlock.kt
  - @app/src/main/java/com/lakasir/acp/data/model/MessagePayloads.kt (`DiffState`)
  - Bridge (not in this repo)
- Current state (after plan 14):
  - The chat sidebar has **Files** and **Git** tabs; Git is a placeholder, shown only when `_lakasir/hello` returns `git: true`
  - `DiffBlock(DiffState(path, oldText, newText))` + `LineDiff` already render line diffs for agent tool calls
  - All workspace calls go through `_lakasir/*` extension methods with paths relative to `cwd`

## Goals
- **Bridge extension methods** (run `git` with an argument array via `spawn`, never through a shell, always `git -C <cwd>`; read-only calls use `--no-optional-locks`):
  - `_lakasir/git/status` `{cwd}` → `{isRepo, branch, upstream?, ahead, behind, detached, files: [{path, origPath?, index: "M"|"A"|"D"|"R"|"?"|..., worktree: ..., conflicted}]}` (parsed from `git status --porcelain=v2 --branch -z`)
  - `_lakasir/git/diff` `{cwd, path, staged}` → `{path, oldText?, newText?, binary, truncated}`
    - unstaged: old = index (`git show :path`), new = working tree file
    - staged: old = `HEAD:path` (missing for new files), new = index
    - deleted file: `newText = ""`, untracked: `oldText = null`; 512 KB cap per side like `fs/read`
  - `_lakasir/git/log` `{cwd, limit = 30, skip = 0}` → `{commits: [{hash, shortHash, subject, author, time}]}`
  - `_lakasir/git/show` `{cwd, hash}` → `{hash, subject, body, author, time, files: [{path, status, additions, deletions}]}` (`git show --numstat`)
  - `_lakasir/git/stage` `{cwd, paths}` (`git add -- <paths>`), `_lakasir/git/unstage` `{cwd, paths}` (`git restore --staged -- <paths>`; for a repo with no commits use `git rm --cached`)
  - `_lakasir/git/commit` `{cwd, message}` → `{hash}` (`git commit -m`; fails with `-32020` "nothing staged" or `-32021` git error with stderr in `data`)
  - Not in a repo → `{isRepo: false}` from `status`; other git calls → `-32022` "not a git repository"
  - Paths go through the same `cwd` guard as plan 14
- **App, data**: `GitApi` (or methods on `WorkspaceApi`) with DTOs `GitStatus`, `GitFileChange`, `GitDiff`, `GitCommit`, `GitCommitDetail`; `WorkspaceRepository` git methods; error mapping for `-32020..-32022`
- **App, Git tab** (`ui/workspace/git/`):
  - Header: branch name (or "detached at <short hash>"), `↑ahead ↓behind` relative to upstream when present (informational only, no push/pull)
  - Sections **Staged**, **Changes**, **Untracked**, **Conflicts** (collapsible, with counts); row = status letter badge (color + letter, not color alone) + path (middle-ellipsized)
  - Row actions: tap → diff view; trailing button **Stage** / **Unstage** (48dp); section header buttons **Stage all** / **Unstage all**
  - Diff view route `GitDiff(sessionId, path, staged)`: reuses `DiffBlock` full-screen; binary/truncated → notice
  - Commit box at the bottom of the tab: multi-line message field + **Commit** button (enabled when something is staged and the message is not blank); success → snackbar "Committed <shortHash>" and refresh
  - **History** section (or a sub-tab): last 30 commits, "Load more"; tap → commit detail (subject, body, author, time, changed files with +/-). Viewing per-file diffs of a past commit is a follow-up
  - `isRepo = false` → `EmptyState` "Not a git repository"
  - Files tab: decorate tree rows with the git status letter/color from the last `status` (modified, added, untracked), when git is available
- **Refresh**: on tab open, pull-to-refresh / header refresh, after stage/unstage/commit, and on the same agent tool-call trigger as plan 14 (debounced 500 ms, shared)

## Assumptions
- Scope is the user-approved default: status, diffs, log, stage/unstage and commit. **No push, pull, fetch, checkout, branch create/switch, stash, discard, or amend**. Discard (`git restore`) is left out because it is destructive; easy follow-up behind a confirm dialog
- Commit author is whatever git config says on the bridge machine; the app does not set name/email. If git has no identity, the `-32021` stderr is shown as is
- Conflicts are shown but not resolved in-app (the user can open the file in the Files editor or ask the agent)
- Depends on plan 14 being done: sidebar, extension protocol, `bridgeFeatures`, path guard
- Hunk/line-level staging is out of scope; stage/unstage is per file

## Notes
- `--porcelain=v2 -z` is stable and handles spaces, renames (`2` entries with `origPath`) and unmerged (`u` entries) paths; don't parse the human format
- Set `GIT_TERMINAL_PROMPT=0`, `GIT_OPTIONAL_LOCKS=0`, `LC_ALL=C` in the child env; kill git after 20 s. None of the included commands touch the network
- `git commit` can run hooks (pre-commit, commit-msg) that take long or fail: use a 60 s timeout for commit only and show hook stderr on failure. Don't pass `--no-verify`
- Concurrency with the agent: the agent may run git at the same time (index.lock). On `index.lock` errors return `-32021` with a clear message; the app shows "Git is busy, try again" and does not retry automatically
- Staging a renamed file must stage both paths (`origPath` and `path`)
- Diff sides come from git objects, not `git diff` text, so `LineDiff`/`DiffBlock` can be reused unchanged. Line endings: normalize `\r\n` for display only
- The commit message field keeps its text across tab switches (ViewModel state) and is cleared only after a successful commit
- Status badges: M modified, A added, D deleted, R renamed, U conflict, ? untracked; colors from the theme's status tokens (plan 02) plus the letter for accessibility
- UI follows `mobile-android-design` with the overview deviations

## Testing
- `./gradlew :app:compileDebugKotlin testDebugUnitTest lintDebug`
- Unit tests (app): DTO parsing of status (renamed, conflicted, untracked, detached, no upstream), diff (new file, deleted, binary), log; grouping of files into Staged/Changes/Untracked/Conflicts (a file modified in both index and worktree appears in both Staged and Changes); commit button enable rule; error mapping
- Bridge tests: porcelain v2 `-z` parser fixtures (spaces in names, rename, unmerged, `# branch.*` headers), argument arrays never contain shell strings, guard applies to `paths`
- Manual against a real bridge in a scratch repo: modify/add/delete/rename files, stage and unstage, view staged and unstaged diffs, commit, check history and commit detail; non-repo `cwd` → empty state; agent edits a file → Changes list updates; repo with a failing pre-commit hook → error shown

## Tools / Skills
- Skill `mobile-android-design` (lists, section headers, text field + button, snackbars)
- Bash: `./gradlew`, `git` (scratch repo for manual tests), bridge test runner (to be confirmed)

## Implementation
- Bridge (`bridge/src/git.js`, wired in `extensions.js`; `hello` now reports `git: true` when `git --version` works):
  - `runGit`: `spawn('git', ['-C', cwd, ...args])`, no shell, `GIT_TERMINAL_PROMPT=0`, `GIT_OPTIONAL_LOCKS=0`, `LC_ALL=C`, 20 s timeout (60 s for commit), 16 MB output cap; `gitError` maps "not a git repository" → `-32022`, `index.lock` → a "Git is busy" message, anything else → `-32021` with stderr in `data`
  - `status`: `git rev-parse --show-prefix` + `git status --porcelain=v2 --branch -z -- .`; `parseStatus` handles branch headers, ordinary/renamed/unmerged/untracked entries and spaces in names; paths are made relative to `cwd` (entries outside the `cwd` subtree are dropped), capped at 1000 files
  - `diff`: sides come from `git show HEAD:./path` / `git show :./path` / the working file, so the app's `LineDiff` is reused; `origPath` for staged renames; 512 KB per side, binary detection
  - `log` (`--skip`/`--max-count`, limited to `-- .`, empty repo → `[]`), `show` (header + `git diff-tree --numstat -r --root -M`; hash validated), `stage` (`git add -A --`), `unstage` (`git restore --staged`, or `git rm --cached` before the first commit), `commit` (`git diff --cached --quiet` check → `-32020`, then `git commit -F -` with the message on stdin; hooks run)
  - Every path goes through the plan 14 guard (`resolveInside`, `follow: false`) and is passed as a `./`-prefixed pathspec after `--`
  - 10 new `node --test` tests (parsers, and handlers against a temporary real repo: subdirectory `cwd`, staged/unstaged/untracked diffs, rename, commit/log/show, empty repo, path guard) → 28 bridge tests
- App:
  - `acp/GitModels.kt` DTOs; `WorkspaceApi.git*` methods (commit uses a 75 s client timeout); `LakasirMethods.GIT_*`, `LakasirErrorCode.NOTHING_STAGED/GIT_ERROR/NOT_A_REPO` + messages in `WorkspaceRepository.errorMessage`
  - `ui/workspace/git/`: `GitGroups` (Conflicts / Staged / Changes / Untracked), `GitDecorations` (Files-tab badges, `•` on folders that contain changes), `GitBadge` (letter, kind, spoken description), branch/sync labels; `GitViewModel` (status + paged log, stage/unstage (+ all), commit message kept in the ViewModel, refresh on tab open, header refresh and agent tool calls)
  - `GitPane`: branch header with ↑/↓ upstream info, collapsible sections with counts, per-row +/− buttons and Stage all / Unstage all, History with Load more, commit box (shown when something is staged or a message is typed) with "Commit N files"
  - Sidebar: `SecondaryTabRow` Files | Git when the bridge reports git; + button hidden on the Git tab; Files rows show the git badge
  - Routes `GitDiffView(sessionId, path, staged, origPath)` → `GitDiffScreen` (full-screen `DiffBlock`, now with a `maxLines` parameter, 3000 lines here) and `GitCommitView(sessionId, hash)` → `GitCommitScreen` (subject, body, author/email, time, hash, changed files with +/−); both use a small generic `GitLoadViewModel`
- Changes from plan:
  - Conflicted files get the stage (+) action too, since `git add` is how a resolved conflict is marked
  - Status, log and diffs cover only the `cwd` subtree when `cwd` is a folder inside a larger repo (paths stay relative to `cwd`). Commit still commits everything staged in the repo
  - Folders in the Files tab get a `•` badge when something inside them changed
- Verified:
  - `./gradlew :app:compileDebugKotlin testDebugUnitTest lintDebug assembleDebug` pass: 108 unit tests (10 new: `GitGroupsTest`, git cases in `WorkspaceApiTest` and `WorkspaceRulesTest`), lint 0 errors and no warnings in the new files
  - `cd bridge && npm test`: 28 pass
  - Smoke test against this repo through a bridge on a spare port: `hello` reports git, and `status`, `log`, `show` and `diff` return correct data (read-only; nothing was staged or committed)
  - Not done: on-device test (the running bridge on :8767 must be restarted to pick up the git methods)
