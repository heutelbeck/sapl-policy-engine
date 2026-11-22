# SAPL 4.1.6 (unreleased)

- Priority-based combining preserves a conflict between different resource transformations on the winning effect, regardless of vote order. A higher-priority effect can still win.
- The canonical policy index retains independent matches when a shared clause belongs to a formula that needs reevaluation after a predicate error.
