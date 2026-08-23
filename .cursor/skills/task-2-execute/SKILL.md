---
name: task-2-execute
description: >
  Execute one Planned/Pending MVP phase task from planning/phases/. Implement
  until acceptance criteria pass, record verification, then hand off to
  /task-3-complete. Manual only — /task-2-execute TXX.
disable-model-invocation: true
allowed-tools: Bash, Read, Grep, Glob, Edit, Write, Task
---

# /task-2-execute — Execute one atomic MVP task

You implement **exactly one** task from `planning/phases/`. Stop when Acceptance
criteria pass (or blocked). Closing the task is **`/task-3-complete`**.

This skill is intended to run on a **[medium] or [small]** model when the
Execution plan from `/task-1-plan` meets the hub handoff bar. Default to
[small] when the plan is thorough; use [medium] for non-trivial implementations
the plan flags as complex. Follow the plan
literally; if the plan is too vague to proceed, **stop** and send back to
`/task-1-plan` (or ask the user to re-plan on a [large] model) — do not invent a
new design. If the plan marks **`Execute model recommendation: large`**, tell
the user before heavy work if they are still on a [small] model.

> 💡 Model sizing is a **recommendation**, not a hard constraint — run this on
> whatever model you have available.  

## Arguments

- Task id: `T01` … `Tnn`  
- If omitted: first INDEX `Planned`, else first actionable `Pending`

## Hard constraints

<!-- BOOTSTRAP: replace with product-specific constraints. -->

1. Obey **Karpathy Behavioral Guidelines** in `.cursor/rules/general.mdc` in full
   (Think Before Coding, Simplicity First, Surgical Changes, Goal-Driven Execution).
   Do not treat "surgical" as the only rule.  
2. Read matching domain spokes before editing those areas.  
3. **New code requires tests** in this task (Kotlin unit tests; table-style where cheap).  
4. **Run the full lint + test (verify) suite** before claiming AC passed.
   Prefer **`make verify`**. Auto-fix first; fail only on remaining unfixable issues.  
5. **Verify tooling presence (hard abort):** Before claiming AC passed, confirm:
   - Root `Makefile` with a `verify` target that runs **lint + test** (+ assembleDebug)
   - Lint config: `app/lint.xml` (Android Lint)
   - `lefthook.yml` with **pre-commit → that verify**
   If any are missing: **stop**, Status `Blocked` (or refuse handoff), tell the user to
   restore verify tooling — do **not** treat `./gradlew test` alone as
   "lint+test green."  
6. Never vendor forbidden reference trees.  
7. Do not commit unless the user explicitly asked (task-3-complete commits on close-out; push only with `--push`).  
8. Only one phase task InProgress unless human approved parallel work.

[large]: https://platform.kimi.ai/docs/guide/kimi-k3-quickstart
[medium]: https://api-docs.deepseek.com/quick_start/pricing
[small]: https://api-docs.deepseek.com/quick_start/pricing  

## Procedure

### 0. Preconditions

- Read task + INDEX  
- Depends-on is `✅` / `Done` / `—`  
- If no Execution plan section, run `/task-1-plan` first  

### 1. Status → `InProgress` (task file + INDEX)

Log Status History.

### 2. Implement

- Follow Execution plan  
- Add/update tests with the code  
- Auto-fix / lint continuously when practical  
- **Delegate bounded subtasks** when they are well-scoped and mechanical
  (behavior-preserving refactors, isolated feature units with a clear spec) to
  implementer / refactorer subagents; keep design decisions and cross-file
  coordination on the parent  
- If you introduce a new library, add/update its `.cursor/rules/<name>.mdc` spoke
  (Docs URL) and a README Dependencies & docs row when practical — otherwise note
  it for `/task-3-complete`  

### 3. Verify gate (mandatory)

**Presence check first** (fail closed):

```bash
test -f Makefile && grep -q '^verify' Makefile
test -f lefthook.yml
test -f app/lint.xml
make verify
```

If `Makefile` / `verify` / `lefthook.yml` (or lint config for this stack) is missing:
stop — Status `Blocked`; do **not** hand off as passed with only package tests.

Run **`make verify`** (lint + tests + build when wired). Record commands and outcomes
in **Verification**. If verify fails: fix or set Status `Blocked`.

**Post-edit checks (when subagents are available):** after non-trivial edits,
launch code-reviewer and test-runner subagents **in the background** and
continue; fold their findings in before claiming AC passed. A reviewer-found
bug blocks the handoff until fixed.

### 4. Acceptance → all checked or Status `Blocked`

AC must include: tests present for new code; lint+test suite green.

### 5. Record on the task file

Fill Verification and Files Modified. Leave INDEX as `InProgress` until
`/task-3-complete`.

### 6. Hand off (mandatory)

Tell the user:

> Run `/task-3-complete TXX` — re-verify → dialectic → INDEX → ✅ → commit (default **no push**; add `--push` to push) → Manual test handoff → next branch.

If the user already asked to execute **and** complete in one go, run
`/task-3-complete` yourself now.

### 7. Output format

```
## Executed — TXX Title
### Result
Acceptance passed / Blocked
### Verification
- lint: …
- tests: …
- …
### Files touched
- …
### Next
/task-3-complete TXX
```

## Do not

- Execute two phase tasks in one invocation  
- Mark INDEX `✅` without `/task-3-complete`  
- Write `Done` into the INDEX Status column  
- Skip tests or skip the lint+test verify gate  
- Treat package tests alone as full verify when Makefile / lefthook / lint config are missing  
