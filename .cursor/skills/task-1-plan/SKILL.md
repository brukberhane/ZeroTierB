---
name: task-1-plan
description: >
  Plan or re-plan a single MVP phase task from planning/phases/. Produce a
  handoff-ready plan so a lesser model can run /task-2-execute. Use a
  [medium] model by default; switch to [large] only for complex tasks.
  Manual only — /task-1-plan TXX.
disable-model-invocation: true
allowed-tools: Bash, Read, Grep, Glob, Edit, Write
---

# /task-1-plan — Plan one atomic MVP task

You refine **one** task file under `planning/phases/` so a **lesser agent**
can run `/task-2-execute` next without rediscovering the design. Use a
**[medium]** model by default; switch to **[large]** only for complex tasks.
You do **not** implement product code unless the user explicitly asks.

> 💡 Model sizing is a **recommendation**, not a hard constraint — use the best
> model you have access to for the task's complexity.
> See hub [Model recommendations](../../README.md#model-recommendations).

## Arguments

- Task id: `T01` … `Tnn` (or path like `planning/phases/T03-….md`)  
- If omitted: read `planning/phases/INDEX.md`, pick the first `Pending`/`Planned`
  task whose Depends-on is `✅` / `Done` / `—`.

## Hard constraints (never violate in the plan)

<!-- BOOTSTRAP: replace this block with product-specific constraints. -->

1. Follow `.cursor/rules/general.mdc` — including **Karpathy Behavioral Guidelines**
   (think / simplicity / surgical / goal-driven) and domain spokes.  
2. Stay inside this task’s Acceptance Criteria — no gold plating (Simplicity First).  
3. Execution plan steps must use `→ verify:` pairs (Goal-Driven Execution).  
4. **Handoff fidelity:** the plan must be technically detailed enough for a
   smaller execute model (see hub “Model split”). Vague plans are not done.  
5. Verify with **`make verify`** (`:app:lintDebug` + unit tests + `assembleDebug`).
   Plans must include **lint + test** gates and **tests for new Kotlin**.  
6. Study `docs/PROXY-VPN-PLAN.md` and archive `archive/proxy-mode` when the task
   says so; **reimplement / port** — do not vendor random ZeroTier trees.  
7. Stack is Android/Kotlin (`com.brukb.zerotier`). Do not add a Go module, bind
   the proxy off loopback, run libzt+JNI together, or write APN proxy fields.  
8. If the task is ambiguous, stop and ask (Think Before Coding) — do not silently pick.  
9. If this task is *exceptionally* hard even with a detailed plan, add
   **`Execute model recommendation: large`** with a one-line why; otherwise omit
   (default = [medium]/[small] execute is fine).  

## Procedure

### 1. Load context

- Read `planning/phases/INDEX.md`  
- Read the target task file end-to-end  
- Read depends-on task’s **Learnings** / **Verification** if `✅` / `Done`  
- Skim `.cursor/rules/general.mdc` + matching domain rules  
- **Delegate the repo reality-check to explorer subagents** (one per independent
  area the task touches) rather than reading file-by-file on the parent; fold
  their findings into the plan. If a fact requires docs, delegate a web-research
  subagent. Small single-file reads may stay inline.

### 2. Reality check

List what exists vs what the task assumes. Update Implementation notes / AC if
the codebase diverged. Record **Reality notes** if upstream task-3-complete left any.

### 3. Write execution plan into the task file

Write for a **junior / lesser-model executor**: no implied context.

```markdown
## Execution plan (filled by /task-1-plan)

**Date:** YYYY-MM-DD
**Codebase snapshot:** …
**Execute model:** [medium]/[small] (default) | large (only if justified below)

### Context for executor
- Goal in one paragraph
- Key files/packages involved (paths)
- Invariants from rules that apply

### Steps
1. … (concrete edit/create) → verify: …
2. … → verify: …

### Tests to add
- Table cases / scenarios …

### Verify commands
- …

### Risks / pitfalls
- …

### Out of scope
- …

### Execute model recommendation
- [medium]/[small] (default) | large — rationale: …
```

Set Status to `Planned` only when a lesser agent could follow the plan cold.
Append Status History row.

### 4. Output to user

Ready? / Key steps / AC / Execute model: [medium](default)|large / Next: `/task-2-execute TXX`

## Do not

- Implement the task  
- Expand scope into later layers  
- Mark INDEX `✅`  
- Leave a hand-wavy plan that forces the execute model to re-design

[large]: https://platform.kimi.ai/docs/guide/kimi-k3-quickstart
[medium]: https://api-docs.deepseek.com/quick_start/pricing
[small]: https://api-docs.deepseek.com/quick_start/pricing  
