---
layout: default
title: Evaluation Semantics
parent: The SAPL Policy Language
nav_order: 111
---

## Evaluation Semantics

This section defines how the SAPL engine evaluates expressions, with particular attention to evaluation order, short-circuit behavior, and how cost strata interact with streaming attribute subscriptions.

For how individual policies and policy sets map evaluation results to decision values (`PERMIT`, `DENY`, `NOT_APPLICABLE`, `INDETERMINATE`), see [Policy Structure](../2_4_PolicyStructure/#policy-evaluation-result) and [Policy Sets](../2_6_PolicySets/#policy-set-evaluation-result).

### Cost-Stratified Short-Circuit Evaluation

All AND and OR operators (`&`, `&&`, `|`, `||`) use **cost-stratified short-circuit evaluation**. The compiler flattens chains of AND/OR operators into N-ary operations. For example, `a && b && c && d` is compiled into a single conjunction rather than a chain of nested binary operations. This enables the engine to sort all operands by cost stratum, regardless of how many there are. If any operand in a lower (cheaper) stratum short-circuits the result, all operands in higher (more expensive) strata are never evaluated and their subscriptions are never created.

### The Three Strata

SAPL categorizes expressions into three strata based on their evaluation cost:

1. **Constants** (e.g., `true`, `false`, `1 + 2`): Evaluated at compile time. This stratum also includes **PDP variables**: values configured by the operator in the [PDP configuration](../2_2_PDPConfiguration/#variables) are known when policies are loaded and are automatically constant-folded into this stratum. A condition referencing a PDP variable (e.g., comparing against a configured tenant name or feature flag) is as cheap to evaluate as a literal `true` or `false`.
2. **Pure expressions** (e.g., `subject.isActive`, `resource.type`): Evaluated at runtime without external subscriptions. This includes the four authorization subscription fields (`subject`, `resource`, `action`, `environment`), which are only known when a concrete subscription arrives.
3. **Streaming expressions** (e.g., `<pip.sensor>`, `subject.<geo.location>`): Require asynchronous subscription to external data sources

### Evaluation Rules

1. **Cross-strata ordering:** Lower (cheaper) strata are always evaluated before higher (more expensive) strata, regardless of operand position in the source.

2. **Within-strata ordering:** Within the same stratum, operands are evaluated strictly left-to-right as they appear in the source.

3. **Short-circuit behavior:** When a short-circuit value is found (`false` for AND, `true` for OR), evaluation stops and remaining operands are not evaluated.

4. **Error propagation:** When an error occurs during evaluation, it propagates immediately. Lower strata errors propagate even if higher strata operands appear earlier in the source.

### Examples

**Constant short-circuits subscription access**

```sapl
subject.isActive && false
```

Since `false` is a constant (lower stratum) that determines the AND result, `subject.isActive` (higher stratum) is **never evaluated**. This is equivalent to just `false`.

**Subscription access short-circuits attribute finder**

```sapl
subject.isAdmin || <pip.externalAuthCheck>
```

If `subject.isAdmin` is `true`, the attribute finder `<pip.externalAuthCheck>` is **never subscribed to**. The external system is never contacted.

**Operand position does not matter for cross-strata**

```sapl
<pip.sensor> && false
```

Even though `<pip.sensor>` appears on the left, the constant `false` is evaluated first. The attribute stream is **never subscribed to**. This may be surprising if you expect strict left-to-right evaluation as in imperative programming languages.

**Left-to-right within the same stratum**

```sapl
true || (1/0 > 0)
```

Both operands are constants (same stratum). Left-to-right order applies: `true` is evaluated first and short-circuits. The division by zero is **never evaluated**, so no error occurs.

```sapl
(1/0 > 0) || true
```

Again both are constants, but now the error-producing expression comes first. The division by zero **is evaluated** and produces an error, even though `true` would have short-circuited if reached.

**Lower strata errors propagate**

```sapl
subject.isActive || (1/0 > 0)
```

Here `subject.isActive` is a pure expression (higher stratum) and `1/0 > 0` is a constant (lower stratum). Constants are evaluated first, so the division by zero **produces an error** before `subject.isActive` is ever checked, even though `subject.isActive` appears first in the source. The pure expression cannot "rescue" the constant error.

### Implications for Policy Authors

{: .info }
**Why this matters for attribute finders:** Attribute finders subscribe to external data sources. Skipping their evaluation when unnecessary avoids unnecessary network calls, reduces latency, and prevents side effects from unused subscriptions. This is particularly valuable when combining quick checks with expensive external lookups.

{: .warning }
**Compile-time errors:** Constant expressions that produce errors (like `1/0`) are evaluated at compile time. These errors propagate regardless of whether a higher-stratum operand (appearing earlier in the source) might have short-circuited at runtime. To avoid this, ensure constant sub-expressions do not contain errors, or guard them with conditional logic.

### Body Condition Evaluation

Each semicolon-terminated statement in a policy body is an operand of an implicit conjunction. The body is equivalent to connecting all its conditions with `&`. The compiler flattens them into a single N-ary AND operation, exactly like an explicit `a & b & c` expression. This means body conditions participate fully in cost-stratified short-circuit evaluation: all conditions are sorted by cost stratum, and if any condition in a cheaper stratum evaluates to `false`, conditions in more expensive strata are never evaluated and their subscriptions are never created.

Combined with the [recommended condition ordering](../2_8_FunctionsAndAttributes/#structuring-policy-conditions) (fast local checks first, PIP lookups later), this ensures that expensive external calls are avoided whenever possible.
