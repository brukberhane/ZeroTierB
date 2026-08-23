---
name: audit-rules
description: >
  Read-only audit of .cursor/rules/*.mdc and .cursor/skills/*/SKILL.md against
  the codebase. Flags staleness, contradictions, missing routing.
  Never auto-removes content.
disable-model-invocation: true
allowed-tools: Bash, Read, Grep, Glob
---

# /audit-rules — Rules + skills audit

**Principles**: `.cursor/rules/general.mdc`  
**Store**: `.cursor/rules/*.mdc` only  

## CRITICAL CONSTRAINTS

1. NEVER auto-remove entries — flag for humans.  
2. NEVER judge whether a latent bug “still applies.”  
3. ONLY flag mechanical staleness, contradictions, gaps, frontmatter issues.  
4. Scaffolding awareness: missing paths expected until phase tasks land →
   `PENDING_SCAFFOLD`, not `BROKEN`. Sequence of record: `planning/phases/INDEX.md`.

## Phase 1 — Inventory

Extract from every rule file: paths, package/component names, problem-class tables,
`last-verified` stamps.

Extract from every skill: frontmatter, path refs, tool allowances.

## Phase 2 — Mechanical verification

- Paths exist or `PENDING_SCAFFOLD`  
- Exports/bindings referenced still exist (grep)  
- Timestamps >6 months → STALE  
- Skills: `name`, `description`, `disable-model-invocation` as expected  

## Phase 3 — Structural audit

- Routing Map ↔ actual `.cursor/rules/*.mdc`  
- Skills inventory in hub ↔ `.cursor/skills/*/`  
- **Hub fidelity:** `general.mdc` still contains **Karpathy Behavioral Guidelines**
  (four sections), **Project Architecture**, **Delivery Principles**, and
  **Rule Maintenance** steps **0–7**. If bootstrap stripped these → `MISSING` /
  dilution gap  
- **Hooks:** lefthook (or documented equivalent) present and wired to lint+test;
  root `Makefile` with `verify`; stack lint config present — flag absence as `BROKEN`
  (execute/complete must hard-abort)  
- **Complete skill:** `/task-3-complete` documents push-by-default, `--no-push`,
  and mandatory Manual test / Nothing to test handoff; verify **presence** abort  
- **Execute skill:** `/task-2-execute` documents the same verify presence abort  
- **Bootstrap skill:** prefers latest stable Go + package baselines; creates
  dependency/docs spokes with official URLs; creates human `README.md`; creates
  stack-appropriate `.gitignore`; ships verify gate (Makefile + lint config +
  lefthook) to project root from `templates/seeds/verify/` /
  `planning/verify-SEED/` (Go Makefile includes multi-platform `build-all`)  
- **Audience split:** named deps appear in both `.cursor/rules/*.mdc` and README
  Dependencies & docs (flag one-sided coverage as `GAP`)  

## Phase 4 — Contradictions

Same symptom + different fix across entries → flag both.

## Phase 5 — Gaps

New packages/domains with no spoke; missing skills for repeated workflows.

## Output format

Use severity table: `BROKEN` | `PENDING_SCAFFOLD` | `STALE` | `MISSING` | `CONTRADICTION` | `GAP`.

End with counts summary.
