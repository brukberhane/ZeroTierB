---
name: bootstrap-turboplan
description: >
  From a written product goal, adapt .cursor/rules and .cursor/skills, seed
  planning/phases, create dependency rule spokes from
  provided docs, write a human README.md, and an appropriate .gitignore.
  Manual only — /bootstrap-turboplan. Does not implement product features.
disable-model-invocation: true
allowed-tools: Bash, Read, Grep, Glob, Edit, Write, WebFetch, WebSearch, Task
---

# /bootstrap-turboplan — Goal → rules + phases + skills + human README

You install or retarget **Turboplan** for this repository. You produce agent
operating infrastructure **and** a human-facing `README.md`. You **do not**
implement product features.

**Two audiences (keep in sync as the project grows):**

| Audience | Primary artifacts |
| -------- | ----------------- |
| **LLMs / agents** | `.cursor/rules/*.mdc`, `.cursor/skills/*/SKILL.md` |
| **Humans** | Root `README.md` (+ phase INDEX for operators) |

## Arguments (user provides in the invocation)

- **Goal** — what users get when done (1–3 paragraphs, end-user perspective)
- **Technical description** — language, runtime, OS, packaging, architecture
- **Non-goals** — explicit exclusions
- **Constraints** — secrets policy, no-gos, verify command, toolchain preferences
- **Dependencies / libraries** — frameworks the product will use (e.g. Cobra,
  SQLite driver, Astro) — each should become a rules spoke
- **References / docs** — paths/URLs to study (official docs, OpenAPI, examples;
  reimplement, do not vendor)
- Optional: preferred layer list / task count target

**CRITICAL — context gathering is mandatory.** If the user invokes `/bootstrap-turboplan`
without a detailed goal and technical description, you MUST ask for them before
proceeding. Do not invent the goal. Do not guess the stack. Ask:

1. What does the user get when the project is done? (end-user perspective)
2. What is the technical scope? (language, runtime, OS, packaging, architecture)
3. What is explicitly out of scope?
4. What dependencies or external APIs will it use?
5. Are there reference implementations to study?

If the answer is vague, ask follow-ups until you have enough to produce an accurate
hub + spokes + phase plan. A human who won't answer these questions isn't ready to
bootstrap — stop and say so rather than producing a wrong architecture.

**The user may provide this context in the initial prompt, in follow-up answers,
or as a file attached to the chat session.** If a file is attached and clearly
contains the project specification (PRD, design doc, notes), read it and
extract the answers — do not re-ask for information already provided.

## Hard constraints

1. Write rules only under `.cursor/rules/`.
2. Delete spokes/skills that cannot apply; replace wrong-provider files (do not light-edit).
3. Seed `planning/phases/INDEX.md` + one stub file per INDEX row.  
4. Adapt skill hard constraints to **this** product.  
5. Do not commit unless the user explicitly asks.  
6. Do not implement product features. Specifically:
   - NEVER create Go source files, `cmd/`, `internal/`, `pkg/`, or `migrations/`
     directories. These belong to T01 via `/task-2-execute`.
   - NEVER run `go mod init` or scaffold a Go module. T01 does that.
   - In-scope (NOT product code): root Makefile, .golangci.yml, lefthook.yml,
     .gitignore, README.md, .cursor/rules/*.mdc, .cursor/skills/*/SKILL.md,
     planning/phases/INDEX.md + stubs.  
7. Bootstrap AC requires **git repo + verify gate files present + lefthook installed**:
   root `Makefile` with `verify` target (lint+test+build for Go), stack lint config,
   and `lefthook install` succeeded. Seeds live under `templates/seeds/` (after install:
   `planning/verify-SEED/`).
   Seeds under `planning/` alone are **not** enough — adapted copies must land
   at repo root + hooks installed. Go Makefiles must include `lint`, `test`,
   `build`, multi-platform `build-all`, and `verify` (= lint+test+build).
   **Do not require `make verify` to pass yet** — there is no source code.
   T01 creates the skeleton; verify passes for the first time in T01.  
8. Use the **stack the user specified** in the technical description. Do not
   silently substitute languages, frameworks, or toolchains. If the user
   specified Go, use Go. If they specified React, use React. Raise any
   non-obvious choices as **bootstrap concerns** with rationale.  
9. Use the **toolchain versions the user specified** (or current environment
    baseline). Do not silently upgrade. If the environment constrains versions,
    document under **Bootstrap concerns**.  
10. For every named dependency / provided doc set that agents will use: create a
    **dedicated spoke** under `.cursor/rules/` with an **External docs** link to
    the official source, and mirror a short human summary in `README.md`.  
11. Create or replace root **`README.md`** with: emoji section headers; ASCII
    banner; a brief human **Summary**; a **Table of Contents** immediately under
    the summary; then the body sections — humans read this; agents read rules/skills.  
12. Create or update root **`.gitignore`** appropriate to the stack — use best
    judgment (always secrets + scratch; language/tooling artifacts as needed).  

## Procedure

### 1. Inventory current agent files

List `.cursor/rules/*.mdc` and `.cursor/skills/*/SKILL.md`. Classify each:
DELETE / ADAPT / KEEP (per Turboplan Guide 01).

**Delegate:** fan out explorer subagents (one per independent area of the
codebase/tree) to build the inventory and assess each file; delegate doc
fetches for dependency spokes to web-research subagents in parallel. Keep
classification **decisions** on the parent.

### 2. Rewrite hub

Update `.cursor/rules/general.mdc`:

- **Strip every reference to Turboplan** — this file is about THIS product, not
  the methodology that bootstrapped it. No "Turboplan hub", no "Turboplan
  project", no "bootstrap-turboplan" in the safety rails exception list.
- **Product name, architecture, build/verify, safety no-gos** — all project-specific
- **Routing Map + Problem Class table** for new spokes (include every dep spoke)
- **Skills inventory** — list all skills that exist in `.cursor/skills/`
- **Layered delivery**: reference `planning/phases/INDEX.md` only. Do NOT copy
  task IDs, layer tables, or phase details into the hub.
- **Dialectic examples** from **this** domain (failure-mode illustrations only)
- **Do NOT include** generic language-preference evangelism ("Prefer Go",
  "Astro vs Vue vs Wails"), generic test/lint/git philosophy, or toolchain
  upgrade policies that match the template defaults. These are methodology
  opinions, not project ground truth. If the user specified a language,
  framework, or toolchain, state it as a fact ("Uses Go 1.26+") not as a
  preference ("Prefer Go because…").  

### 3. Spokes (domains + dependency docs)

- Delete obsolete  
- Create/adapt **product domain** spokes with invariants + at least one symptom table skeleton  
- Language craft spoke if applicable (`go.mdc`, etc.)  
- **Dependency / docs spokes (mandatory when deps or docs are provided):**

  For each library, framework, CLI toolkit, or external API the Goal/Constraints/
  Dependencies/References name (e.g. Cobra, Moonshot API, Cloudflare):

  1. Prefer **official documentation** (fetch or use provided URLs).  
  2. Create `.cursor/rules/<name>.mdc` (e.g. `cobra.mdc`) including:
     - Title + when to apply (`globs` if useful)  
     - **`Docs (if stuck):`** canonical URL near the top (e.g. https://cobra.dev/docs/)  
     - Scope / invariants / layout or API conventions for **this** product  
     - At least one symptom / cause / fix table skeleton (or real patterns from docs)  
     - `<!-- last-verified: YYYY-MM -->`  
  3. Add the spoke to the hub **Routing Map** and Problem Class table.  
  4. Summarize the same dependency for humans in `README.md` → **Dependencies & docs**
     (name, role, docs URL, link to the `.mdc` spoke).  

  Use `templates/rules/EXAMPLE-domain.mdc` as the shape. Delete `EXAMPLE-domain.mdc`
  when real spokes exist.

### 4. Skills

Ensure these exist and hard constraints match the product:

- `grill-me`, `task-1-plan`, `task-2-execute`, `task-3-complete`, `dialectic-of-cognition`, `audit-rules`, `setup-tasks`  
- Keep `bootstrap-turboplan` for future retargets  

Remove skills that only serve deleted stacks.

### 4b. Clean up installer leftovers

After verify gate files are adapted to root, delete these stale seeds:

```bash
rm -f planning/_TEMPLATE.md
rm -rf planning/verify-SEED
rm -f planning/README-SEED.md
rm -f planning/gitignore-SEED
```

### 5. Git repo + verify tooling + lefthook (bootstrap acceptance — mandatory)

1. If `.git` is missing: `git init` in the project root (empty repo / first commit
   later is fine). If `.git` already exists, leave it — do not destroy history.  
2. **Ship the verify gate at project root** (adapt seeds; do not leave them only
   under `planning/`):
   - Prefer pack seeds: `templates/seeds/verify/` (after install:
     `planning/verify-SEED/`) — `Makefile`, `lefthook.yml`, `golangci.yml` →
     root as `Makefile`, `lefthook.yml`, `.golangci.yml` (rename for Go).  
   - Root **`Makefile`** must expose **`verify`** that runs **lint + test**
     (+ build when applicable). Document `make verify` and `make install-hooks`
     in hub Build & Run **and** README.  
   - Root **`lefthook.yml`**: pre-commit → `make verify` (not ad-hoc partial checks).  
   - Stack lint config (Go: **`.golangci.yml`**). Adapt if non-Go; do not skip.  
3. **Go projects (mandatory Makefile shape):** start from `seeds/verify/Makefile`
   and keep (or equivalent) targets:
   - `lint` — golangci-lint  
   - `test` — `go test ./...`  
   - `build` — host `go build ./...`  
   - `build-all` — multi-platform cross-compile into `dist/` (linux/darwin ×
     amd64/arm64 by default; override `BINARY` / `MAIN_PKG` / `GOOS_LIST`)  
   - `verify` — `lint` + `test` + `build` (this is the gate; not `build-all`)  
   - `fmt`, `vet`, `clean`, `install-hooks`, `help`  
   Adapt package paths (`MAIN_PKG`) to the real `cmd/` layout.  
4. Run **`lefthook install`** (or `make install-hooks`) so hooks are active in
   this clone.  
5. Bootstrap AC **fails** if any of these files are missing, if `verify` target is not
   lint+test(+build), or if hooks are not installed / do not invoke verify.
   **Do not require `make verify` to pass** — the repo has no source code yet.
   T01 creates the skeleton; verify first passes in T01.  
6. `/task-2-execute` and `/task-3-complete` must **hard-abort** when this tooling
   is later deleted — bootstrap must leave them unable to “pass” on tests alone.

### 6. Latest stable toolchain + packages (mandatory)

1. Install or upgrade the project's **primary language** to the **latest stable**
   release available for the host OS/arch (use the package manager the user
   already employs). Record the version in hub Build & Run + README Status.  
2. When scaffolding modules: use current stable dependency versions
   (`go get` current stables for Go, `go mod tidy`; for JS: `yarn` / `npm`
   stable versions; avoid knowingly unstable majors).  
3. Install lint/format tools at current stable (e.g. golangci-lint for Go,
   ESLint/Prettier for JS) so verify works.  
4. If policy or the machine blocks upgrades, list under **Bootstrap concerns**
   with the pinned version — do not silently stay on stale toolchains when an
   upgrade is possible.  

### 6b. `.gitignore` (mandatory)

Create or update root **`.gitignore`**. Prefer
`templates/seeds/gitignore/gitignore-SEED` / installed `planning/gitignore-SEED`
as a starting checklist, then **adapt with best judgment** for this product.

**Always include (unless the user explicitly wants otherwise):**

- Secrets / env: `.env`, `.env.*` (optionally allow `!.env.example` / `!.env.ref`)  
- Scratch: root-level `tmp/` (and similar local scratch dirs)  

**Stack-aware (enable what applies):**

- **Go:** binaries / `*.exe` / `*.test` / coverage profiles; local `bin/` or `dist/`
  if used; do not ignore source. Prefer not ignoring `vendor/` unless the project
  policy is to never commit it.  
- **Node / Astro / Vue / Wails frontend:** `node_modules/`, build outputs (`dist/`,
  `.astro/`, etc.), Yarn cache noise per project linker  
- **Python:** `__pycache__/`, `.venv/`  
- **OS / editors:** `.DS_Store`, common IDE folders if not already shared  

If `.gitignore` already exists: **merge** — add missing essential patterns; do not
wipe custom entries. Never commit real secrets to “fix” ignore gaps — fix the
ignore file instead.

### 5. Seed phases

1. Derive layered build order from the goal (Guide 02)  
2. Write `planning/phases/INDEX.md` with T01…Tnn

**T01 is always the app boilerplate bootstrap task (L0 skeleton).**
It creates the minimal runnable program so that `make verify` passes:
- For a Go app: `go mod init`, `cmd/<name>/main.go` (minimal: flag or `fmt.Println`),
  `internal/` directory tree (`doc.go` stubs), and the root Makefile verify target.
- No business logic, no features — just the skeleton that compiles.
- After T01, `make verify` must pass (lint + build + test on the skeleton).  
3. Create each stub from the Turboplan task template (Description, Requirements, AC, empty Execution plan section, Depends-on, Next, Layer)  
4. Final task should be E2E / holistic verification  
5. Early tasks should include verify wiring / sample test+lint green if scaffold exists  

### 6. Human README.md (mandatory)

Create or rewrite root **`README.md`** for humans (not a dump of agent rules).

Prefer the pack seed `templates/seeds/readme/README-SEED.md` (after install:
`planning/README-SEED.md` if the installer copied it):

1. **ASCII banner** for the product name (block letters in a fenced `text` block)  
2. **Summary** — brief human-readable overview (2–4 sentences: what / who / done)  
3. **Table of Contents** — immediately below the summary (link major sections)  
4. **Emoji section headers** for the body (problem, fix, status, layout, deps, building, security, …)  
5. Pitch details, status pointing at `planning/phases/INDEX.md`  
6. Repo layout table  
7. **Dependencies & docs** table (synced with dep spokes from §3)  
8. **Building with Turboplan** section — link to
   [https://github.com/commoddity/turboplan](https://github.com/commoddity/turboplan)
   and show the plan → execute → complete loop; also link to INDEX  
9. Security / invariants relevant to humans  
10. Point methodology at [Turboplan](https://github.com/commoddity/turboplan)  

If a README already exists: adapt it to this shape — do not leave a stale fork
README that contradicts the new goal. Keep factual content that still applies.

**Ongoing:** as phases complete and architecture changes, keep README and rules
aligned (execute/complete/dialectic should update human docs when the story for
humans changed — not only `.mdc` files).

### 7. Self-check

- [ ] Routing Map ↔ rule files bijection  
- [ ] Git repo exists; root `Makefile` has `verify` (lint+test); lint config present;
      lefthook (or approved equivalent) installed and runs that verify on pre-commit
      (note: `make verify` may fail — no source code yet; T01 makes it pass)  
- [ ] Primary language (and other baseline tools) at **latest stable**; deps installed at current stables (or concern documented)  
- [ ] Root `.gitignore` present (`.env`/variants, `tmp/`, stack artifacts — merged if pre-existing)  
- [ ] Every provided dependency/docs set has a spoke with **Docs (if stuck)** URL + README row  
- [ ] Root `README.md` exists with ASCII banner, **Summary**, **TOC**, emoji headers, deps section  
- [ ] No leftover deleted-stack names in rules/skills (grep)  
- [ ] Every INDEX row has a stub  
- [ ] Depends-on graph acyclic; T01 actionable  
- [ ] No references to "Turboplan" in `.cursor/rules/general.mdc` (aside from the skills inventory which lists `bootstrap-turboplan` as a skill name)  
- [ ] Hub states stack choices as facts ("Uses Go 1.26+, React 19") not as methodology preferences ("Prefer Go", "Astro vs Vue vs Wails")
- [ ] "Karpathy Behavioral Guidelines" heading present in hub (not "Behavioral Guidelines")  
- [ ] No task IDs or layer tables in general.mdc (INDEX.md is the sole source of truth)  
- [ ] No product source code created (no `cmd/`, `internal/`, `pkg/`, `migrations/`)  
- [ ] Installer leftovers cleaned (no `_TEMPLATE.md`, `verify-SEED/`, `README-SEED.md`, `gitignore-SEED` under `planning/`)  
- [ ] `setup-tasks` skill present in `.cursor/skills/` and listed in hub skills inventory  
- [ ] T01 is the app skeleton bootstrap task (module init, main.go, directory tree, `make verify` passes)  

### 8. Output to user

```
## Bootstrap complete — {{PRODUCT}}

### Rules
- hub: …
- spokes: … (include dep spokes + docs URLs)
- deleted: …

### README
- path: README.md
- deps documented: …

### Gitignore
- patterns: .env* · tmp/ · <stack-specific> …

### Stack
- backend: {{BACKEND_LANG}}
- frontend: {{FRONTEND_FRAMEWORK}} | n/a
- concerns: … / none

### Git / hooks
- git: initialized | already present
- Makefile verify: present → `make verify` (= lint + test + build); Go also has `build-all`
- lint config: .golangci.yml | <stack> | MISSING (fail)
- lefthook: configured + installed → pre-commit runs make verify
- seeds: `templates/seeds/` → `planning/*-SEED*` → adapted to root
- toolchain: {{LANGUAGE}} <version> (latest stable) | pinned concern: …
- packages: latest stable baseline | concern: …

### Phases
- T01…Tnn listed
- First action: /task-1-plan T01

### Skills
- adapted: …

### Review gate
Please confirm architecture + layer order + README before /task-1-plan T01.
```

## Do not

- Start `/task-2-execute` unless the user explicitly chains it  
- Invent PBIs outside `planning/phases` unless the repo already uses another backlog and the user asks to bridge  
- Copy secrets from references into the repo  
- Skip git init / Makefile verify / lefthook and call bootstrap done  
- Leave verify seeds only under `planning/` without copying adapted files to project root  
- Leave any "Turboplan" reference in `.cursor/rules/general.mdc` (the skill inventory listing `bootstrap-turboplan` is the only exception)
- State stack choices as methodology preferences — state them as facts  
- Skip a dep/docs spoke when the user named that dependency or provided its docs  
- Skip creating/updating human `README.md`  
- Skip creating/updating `.gitignore` (or leave secrets/tmp unignored)  
