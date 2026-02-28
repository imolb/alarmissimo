# GitHub Copilot – Workspace Instructions

Apply the following rules to **every** request in this workspace.

## 1 — Keep the specification in sync
If a code change affects behaviour, data models, UI, or architecture described in
`spec/alarmissimo_specification.md`, update the relevant section(s) of the spec
**in the same response**, not as a follow-up.

## 2 — Propose options when trade-offs exist
When multiple meaningfully different implementation approaches are applicable
(e.g. different APIs, architectural patterns, UX designs), briefly list them with
pros and cons **before** implementing, and either ask which to use or state which
you chose and why.
Do **not** do this for trivial decisions where one option is clearly best.

## 3 — Parallelise: do non-interactive tasks first
If some sub-tasks can be completed without user input and others require
clarification or a decision, complete the independent tasks first and ask follow-up
questions only for the remaining ones.

## 4 — Build and fix after every change
After implementing any change, trigger `./gradlew app:assembleDebug` (or
`app:compileDebugKotlin` for a faster check) and resolve all errors and warnings
before handing back to the user.
Report the final build status explicitly.
