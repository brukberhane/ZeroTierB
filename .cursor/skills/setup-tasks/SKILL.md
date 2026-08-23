---
name: setup-tasks
description: >
  Pre-planning step for a new feature or addition to an existing project.
  Gathers context, reads current rules and INDEX, and proposes new
  phase stubs without rewriting existing infrastructure. Manual only — /setup-tasks.
disable-model-invocation: true
allowed-tools: Bash, Read, Grep, Glob, Edit, Write, WebFetch, WebSearch, Task
---

# /setup-tasks — New feature → context → phase stubs

You add tasks for a new feature, subsystem, or capability expansion in an
**already-bootstrapped** project. You do **not** rewrite rules,
skills, or the hub. You only produce new `planning/phases/TXX-….md` stubs
appended to INDEX.

## Arguments (user provides in the invocation)

- **Feature goal** — what the user gets when this feature is done (1–3 paragraphs)
- **Technical scope** — what parts of the codebase this touches, what new deps it needs
- **Non-goals** — explicit exclusions
- **Constraints** — any new constraints specific to this feature
- **Dependencies / libraries** — new frameworks or APIs (each needs a rules spoke)
- **References** — code, docs, or examples to study

**Context gathering is mandatory.** Same protocol as `/bootstrap-turboplan`:
if the human provides a vague one-liner, ask the detailed questions before
writing tasks. A feature too vaguely described to plan is a feature the human
hasn't thought through yet — stop and say so.

**Preferred input:** a completed `/grill-me` session. When the human arrives
from `/grill-me`, the settled decisions in its shared-understanding summary
answer most context questions already — do not re-ask for information already
settled there; read the summary (and its decision log) and grill only what it
left open.

## Procedure

### 1. Read current state

- Read `.cursor/rules/general.mdc` — understand architecture, safety rails, stack
- Read `planning/phases/INDEX.md` — know what exists, what's done, what's next
- Read any relevant domain spokes for the feature area
- **Delegate codebase exploration to explorer subagents** (one per independent
  area the feature touches) instead of reading file-by-file on the parent;
  fan them out in parallel and fold findings in. Small greps may stay inline.

### 2. Gather context

Ask the human the same detailed questions as bootstrap:

1. What does the user get when this feature is done?
2. What is the technical scope? (which files/packages, new deps, new APIs)
3. What is explicitly out of scope?
4. What new dependencies or external APIs will it use?
5. Are there reference implementations to study?

### 3. Propose tasks

1. Determine where in the layer order the new tasks fit (respect existing Depends-on graph)
2. Create `planning/phases/TXX-….md` stubs using the task template:
   - Description, Requirements, Acceptance Criteria, empty Execution plan
   - Depends-on pointing to existing completed tasks or new preceding stubs
   - Layer matching the build order
3. Append rows to `planning/phases/INDEX.md` with proper Depends-on / Next links
4. If the feature introduces a new dependency, ask the human whether to create
   a dependency spoke (or note it for bootstrap retarget)

### 4. New dependency spokes (optional, human-approved)

If the feature introduces a named library, framework, or external API not yet
covered by a spoke:

1. Ask the human: "Create a `.cursor/rules/<name>.mdc` spoke for <dep>?"
2. If yes: fetch official docs, write spoke with symptom/cause/fix table skeleton,
   add to hub Routing Map
3. Mirror in README → Dependencies & docs

### 5. Output

```
## /setup-tasks complete — {{FEATURE}}

### Context gathered
- Goal: …
- Scope: …
- Non-goals: …
- New deps: … / none

### New tasks
| ID | Title | Layer | Depends-on |
| -- | ----- | ----- | ---------- |
| TXX | …    | LX    | TYY        |

### INDEX updated
- Appended after TYY, before (existing next task)

### First action
- /task-1-plan TXX
```

## Do not

- Rewrite the hub, existing rules, or skills
- Delete or reorder existing INDEX rows
- Implement product code
- Skip context gathering because "the human seems busy"
- Create dependency spokes without human approval (propose; don't decide)
