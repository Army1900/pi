# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read AGENTS.md first

`AGENTS.md` is the **authoritative source for development rules** (style, git, deps, releasing, testing). It applies to both humans and agents. This file summarizes the architecture and the most load-bearing rules; when anything here and AGENTS.md disagree, AGENTS.md wins. Re-read it before any non-trivial change.

## What this is

Pi is a self-extensible coding agent. This is the `pi-monorepo` — an npm workspaces TypeScript monorepo (ESM, Node >=22.19) that produces the `pi` CLI plus the libraries behind it.

| Package | Role |
|---------|------|
| `@earendil-works/pi-tui` | Terminal UI library with differential rendering |
| `@earendil-works/pi-telemetry` | Vendor-neutral telemetry contracts, adapters, typed schemas |
| `@earendil-works/pi-ai` | Unified multi-provider LLM API (OpenAI, Anthropic, Google, Bedrock, …) with model discovery |
| `@earendil-works/pi-agent-core` | Agent runtime: loop, state, and the durable **AgentHarness** |
| `@earendil-works/pi-protocol` | Transport-neutral CBOR protocol for remote sessions |
| `@earendil-works/pi-client` | Client for remote sessions over framed CBOR |
| `@earendil-works/pi-server` | Server for remote sessions (single-writer, snapshot + live events) |
| `@earendil-works/pi-coding-agent` | The `pi` CLI — read/bash/edit/write tools, skills, prompts, extensions |
| `@earendil-works/pi-evals` | Eval harness |

Session storage backends live under `packages/session-backends/` (e.g. `sqlite-node`).

## Commands

```bash
npm install --ignore-scripts   # ALWAYS --ignore-scripts (lifecycle scripts are reviewed code)
npm run build                  # refresh model data + build all packages in dependency order
npm run build:offline          # rebuild using existing model data, no network
npm run check                  # biome (lint+format, --write) + pinned-deps + ts-imports + shrinkwrap + tsgo --noEmit + browser-smoke
./test.sh                      # ALL non-e2e tests (from repo root). Do NOT run vitest directly.
./pi-test.sh                   # run pi from sources, from any directory
```

Running a single test (from the package root):

```bash
# vitest packages
node "$(git rev-parse --show-toplevel)/node_modules/vitest/dist/cli.js" --run test/specific.test.ts
# packages/tui uses node:test
node --test test/specific.test.ts
```

Gotchas:
- **Never** run `npm run build`, `npm test`, or the bare `vitest` suite unless the user asks — the suite auto-runs paid e2e provider tests when auth env vars are present. Use `./test.sh` for everything non-e2e.
- After **code** changes (not docs), run `npm run check` and fix all errors/warnings/infos before stopping. It does not run tests.
- The pre-commit hook (`.husky/pre-commit`) runs `npm run check` and the lockfile gate. Commits will be rejected on failure.

## Architecture

### Build dependency order

The root `build` script chains packages in dependency order; mirroring this, you generally must build dependencies before dependents:

`tui → telemetry → ai → agent → session-backends/sqlite-node → protocol → client → server → coding-agent`

Cross-package imports use the `@earendil-works/pi-*` workspace names; `npm run build` resolves them via the workspace `dist/` outputs.

### The AgentHarness (current design focus)

`packages/agent/src/harness/` implements a **durable, explicit-state agent harness** — the active area of work. Design docs: `packages/agent/docs/harness-v2.md`, `harness-v2-state-machine.md`, `harness-v2-test-matrix.md`. Read these before touching the harness. Core ideas:

- **One harness executes runs against one session.** Sessions have four durable parts: the append-only conversation **tree** (entries with `parentId`), **lanes** (named positions, each with one operation, running in parallel), **lane records** (flat chronological operation log), and **global facts** (last-write-wins session-scoped values).
- **Durable runs.** An accepted prompt is a durable operation; after a crash, a new process reconstructs it from records and resumes from the last durable boundary. No partial outcomes are observable.
- **Single writer.** One process writes a session at a time; the serving layer enforces this. Multi-process/replication are explicitly out of scope.
- **Events observe, hooks intercept.** Events cannot change execution; hooks (context, requests, tools, run boundaries) can. Extensions build on both.
- **`drive: "manual"`** parks the harness before each effect (durable write, provider request, tool call, hook) so tests drive it boundary-by-boundary and can simulate crashes — production and tests run the same procedures.
- **Compatibility policy:** only coding-agent **v3** JSONL sessions require backward compatibility (must open and restore idle). Other formats/APIs/tests in `harness/` and `session-backends/sqlite-node` may break freely without migrations.

The older non-harness API in `packages/agent/src/` (`agent.ts`, `agent-loop.ts`, `types.ts`) predates the harness.

### pi-coding-agent (the `pi` CLI)

`packages/coding-agent/src/` layers: `cli.ts`/`main.ts` entry → `core/` (session runtime, tools, extensions, skills, model/provider resolution, system prompt, compaction) → `modes/` (`interactive` TUI, `rpc`, `print-mode`, `json-event`). Built-in tools live in `core/tools/` (read, write, edit, bash, find, grep, ls, image). Extensions (`.pi/extensions`) and skills (`.pi/skills`, `SKILL.md`) are loaded and surfaced to the model. Remote sessions go through `client/` and `server/` over `pi-protocol`.

### pi-ai (multi-provider LLM API)

`packages/ai/src/` normalizes OpenAI/Anthropic/Google/Bedrock/etc. behind one `Model`/`Transport`/`SimpleStreamOptions` surface, with auth (`oauth.ts`, `env-api-keys.ts`), image models, and a model catalog. Per-provider code is under `providers/`.

### Telemetry

`packages/telemetry` defines vendor-neutral contracts; the schema is documented in `packages/agent/docs/telemetry-schema.md`.

## Code conventions that bite

These are enforced by `npm run check` or are load-bearing project rules — see AGENTS.md for the full list.

- **Erasable TypeScript only** in everything checked by the root config (`packages/*/src`, `packages/*/test`, `packages/coding-agent/examples`): `erasableSyntaxOnly: true`. No parameter properties, `enum`, `namespace`/`module`, `import =`, `export =`. Use explicit fields with constructor assignments.
- **No inline imports** — no `await import()`, `import("pkg").Type`, or dynamic type imports. Top-level imports only. (`check:ts-imports` also enforces relative-import style.)
- **No `any` unless absolutely necessary.** Don't downgrade code to fix type errors from outdated deps — upgrade the dep.
- **Formatting:** Biome, **tabs**, indent width 3, line width 120. `npm run check` auto-formats (`--write`).
- **Generated files — never hand-edit:** `packages/ai/src/models.generated.ts` and `image-models.generated.ts`. Edit `packages/ai/scripts/generate-models.ts` then run `npm run generate:models` (or `build`). Including the resulting generated diff in a commit is always fine.
- Read files **in full** before wide-ranging edits; don't rely on search snippets.

## Git & dependencies (critical)

Multiple pi sessions may run in this cwd simultaneously. Stomping on another session's files is the main failure mode — AGENTS.md's Git section is mandatory reading. Highlights:

- Only commit files **you** changed this session. Stage explicit paths (`git add <file>`), never `git add -A`/`git add .`. Run `git status` and verify before committing.
- Never run `git reset --hard`, `git checkout .`, `git clean -fd`, `git stash`, `git add -A/.`, or `git commit --no-verify`.
- Commit message format: `{feat,fix,docs}[(ai,tui,agent,coding-agent)]: <message>`. Only commit when the user asks.
- **Lockfile changes are reviewed code.** The pre-commit hook blocks `package-lock.json` unless `PI_ALLOW_LOCKFILE_CHANGE=1`. Direct external deps are pinned exact; `--ignore-scripts` is mandatory on all installs.
- `packages/ai/src/models.generated.ts` may always be staged alongside your files.

## Releasing

Lockstep versioning: all packages share one version, updated together (`patch` = fixes+additions, `minor` = breaking, no majors). The release flow (CHANGELOG audit → local smoke test → `npm run release:patch`/`release:minor` → CI trusted publish → R2 announcement marker) is detailed in AGENTS.md; do not attempt a release without re-reading it.
