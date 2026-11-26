# SAPL 4.1.6 (unreleased)

- Updated Vaadin to 25.3.6 to fix SVG icon handling in [CVE-2026-136173](https://vaadin.com/security/cve-2026-136173).
- Priority-based combining preserves a conflict between different resource transformations on the winning effect, regardless of vote order. A higher-priority effect can still win.
- The canonical policy index retains independent matches when a shared clause belongs to a formula that needs reevaluation after a predicate error.
- Policy set targets and enforced schemas can use set variables. Variables declared by the last child policy stay local to that policy.
- The policy index preserves object-key type errors from grouped `has` conditions while evaluating ordinary equality conditions on the same operand.
- CANONICAL and SMTDD preserve errors in complementary conditions. For example, `subject && !subject` is false for boolean subjects and reports a type error for a text subject, as it does with NAIVE.
- Protobuf preserves readable plain decimal encodings and uses a bounded decimal spelling when needed. Writing a number with no representation accepted by the 4.1.x reader raises `IOException`.
