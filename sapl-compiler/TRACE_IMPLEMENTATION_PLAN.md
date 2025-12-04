# Trace Implementation Plan

This plan implements end-to-end metadata tracing in three levels, updating combining
algorithms progressively. Each level builds on the previous.

See `TRACE_IMPLEMENTATION.md` for full design rationale.

## Prerequisites

- [x] ValueMetadata propagation in expressions (already implemented)
- [x] AttributeRecord creation in AttributeCompiler (already implemented)

## Level 1: TracedPolicyDecision

**Goal:** Create the atomic traced decision unit for individual policy evaluation.

### Step 1.1: Create TraceFields constants

File: `io.sapl.api.pdp.internal.TraceFields`

Constants for all field names used in trace Values. Prevents typos and enables
refactoring.

### Step 1.2: Create TracedPolicyDecision builder

File: `io.sapl.api.pdp.internal.TracedPolicyDecision`

Builder for Level 1 traced decisions:
- name, entitlement
- Full decision: decision, obligations, advice, resource
- attributes (converted from AttributeRecord)
- errors

Include accessor methods for reading built Values.

### Step 1.3: Create AttributeRecord → Value conversion

Utility method to convert `AttributeRecord` POJO to Value for inclusion in trace.
Location in `TracedPolicyDecision` or separate utility.

### Step 1.4: Modify SaplCompiler.compileDecisionExpression to produce TracedPolicyDecision

The key insight is that trace building happens at compile time in SaplCompiler, wrapping
the decision expression. This preserves constant folding:

**Constant folding for traces:**
```
policy "always-permit" permit
→ Constant TracedPolicyDecision { name, entitlement, decision, [], [], UNDEFINED, [], [] }
   (No PIPs → attributes empty → fully constant at compile time)

policy "check-role" permit where subject.role == "admin"
→ PureExpression producing TracedPolicyDecision
   (No PIPs in condition → attributes still empty → computed at runtime but no metadata)

policy "check-clearance" permit where subject.<clearance> >= 5
→ PureExpression/StreamExpression producing TracedPolicyDecision
   (PIP used → attributes extracted from metadata at runtime)
```

**Implementation approach:**

Modify `compileDecisionExpression()` to wrap the result:

```java
private CompiledExpression compileDecisionExpression(Policy policy, CompilationContext context) {
    val name = policy.getSaplName();
    val entitlement = decisionOf(policy.getEntitlement()).name();
    val decisionExpr = compileDecisionExpressionInternal(policy, context);

    return wrapWithTrace(name, entitlement, decisionExpr);
}

private CompiledExpression wrapWithTrace(String name, String entitlement, CompiledExpression decisionExpr) {
    // Constant folding: if decision is constant, trace is constant
    if (decisionExpr instanceof Value decisionValue) {
        return TracedPolicyDecision.builder()
            .name(name)
            .entitlement(entitlement)
            .fromDecisionValue(decisionValue)  // extracts decision, obligations, etc.
            .attributes(List.of())  // No PIPs for constant → empty
            .errors(extractErrors(decisionValue))
            .build();
    }

    // Pure path: wrap to build trace at evaluation time
    if (decisionExpr instanceof PureExpression pureExpr) {
        return new PureExpression(ctx -> {
            val decisionValue = pureExpr.evaluate(ctx);
            return TracedPolicyDecision.builder()
                .name(name)
                .entitlement(entitlement)
                .fromDecisionValue(decisionValue)
                .attributes(convertAttributes(decisionValue.metadata().attributeTrace()))
                .errors(extractErrors(decisionValue))
                .build();
        }, pureExpr.isSubscriptionScoped());
    }

    // Stream path: map each emission to traced decision
    if (decisionExpr instanceof StreamExpression streamExpr) {
        return new StreamExpression(streamExpr.stream().map(decisionValue ->
            TracedPolicyDecision.builder()
                .name(name)
                .entitlement(entitlement)
                .fromDecisionValue(decisionValue)
                .attributes(convertAttributes(decisionValue.metadata().attributeTrace()))
                .errors(extractErrors(decisionValue))
                .build()
        ));
    }

    throw new IllegalStateException("Unexpected expression type");
}
```

**Key files:**
- `SaplCompiler.java` - wrap decision expression with trace building
- `TracedPolicyDecision.java` - builder with `fromDecisionValue()` convenience method

### Step 1.5: Update CompiledPolicy record

The `CompiledPolicy` no longer needs entitlement as a separate field since it's now
embedded in the TracedPolicyDecision that `decisionExpression` produces.

However, we may still want it for debugging/logging. Evaluate during implementation.

```java
// Option A: Keep simple (entitlement in trace)
public record CompiledPolicy(
    String name,
    CompiledExpression matchExpression,
    CompiledExpression decisionExpression  // Now produces TracedPolicyDecision
) {}

// Option B: Add entitlement for convenience
public record CompiledPolicy(
    String name,
    String entitlement,
    CompiledExpression matchExpression,
    CompiledExpression decisionExpression
) {}
```

Decide based on what CombiningAlgorithmCompiler needs access to.

### Step 1.6: Tests for Level 1

- Builder produces correct structure
- Accessors read fields correctly
- AttributeRecord conversion works
- CompiledPolicy includes entitlement

---

## Level 2: TracedPolicySetDecision

**Goal:** Update combining algorithms to buffer and compose Level 1 decisions.

### Step 2.1: Create TracedPolicySetDecision builder

File: `io.sapl.api.pdp.internal.TracedPolicySetDecision`

Builder for Level 2 traced decisions:
- name, type ("set")
- algorithm
- Full decision: decision, obligations, advice, resource
- policies[] (array of Level 1 Values)

### Step 2.2: Determine single-policy document representation

Evaluate Option A (flat) vs Option B (wrapped) with real code.
Choose based on which produces cleaner, more readable implementation.

Option A - flat:
```json
{ "name": "doc", "type": "policy", "entitlement": "PERMIT", "decision": "PERMIT", ... }
```

Option B - wrapped:
```json
{ "name": "doc", "type": "policy", "policy": { /* TracedPolicyDecision */ } }
```

### Step 2.3: Refactor CombiningAlgorithmCompiler - data structures

Since Level 1 changed `decisionExpression` to produce TracedPolicyDecision Values directly,
the combining algorithm now receives traced decisions, not raw decisions.

Update internal data structures:
- `PolicyEvaluation` record may be replaced by direct use of TracedPolicyDecision accessors
- `DecisionAccumulator` buffers TracedPolicyDecision Values (immutable, safe to collect)
- No metadata extraction needed here - already in the TracedPolicyDecision

Key change: `evaluatePolicyDecision()` now returns TracedPolicyDecision Value, not raw decision.

### Step 2.4: Refactor CombiningAlgorithmCompiler - pure path

Update `evaluateGenericPure()` and similar methods:
1. Evaluate each matched policy
2. Build TracedPolicyDecision for each
3. Buffer in accumulator
4. After combining, build TracedPolicySetDecision with buffered policies

### Step 2.5: Refactor CombiningAlgorithmCompiler - stream path

Update `buildGenericStream()` and similar methods:
1. Each policy Flux produces traced policy decision
2. `combineLatest` combines traced decisions
3. Build TracedPolicySetDecision from latest values

Challenge: In streaming, policy traces update independently. Need to track
"latest traced decision per policy" and rebuild set trace on each emission.

### Step 2.6: Handle first-applicable algorithm

First-applicable has different semantics - it short-circuits.
- Only evaluate until first applicable policy found
- Trace should only contain policies actually evaluated
- Stream implementation uses nested `switchMap`

### Step 2.7: Tests for Level 2

- Set builder produces correct structure
- Combining algorithms produce traced output
- Multiple policies combined correctly
- First-applicable traces correctly
- Stream path produces correct traces
- Pure path produces correct traces

---

## Level 3: TracedPdpDecision

**Goal:** Complete the trace at PDP level with full metadata.

### Step 3.1: Create TracedPdpDecision builder

File: `io.sapl.api.pdp.internal.TracedPdpDecision`

Builder for Level 3 traced decisions:
- Full decision: decision, obligations, advice, resource
- Trace: pdpId, configId, subscriptionId, subscription, timestamp, algorithm
- documents[] (array of Level 1 or 2 Values)
- modifications[]

### Step 3.2: Create TracedDecisionOperations utility

File: `io.sapl.api.pdp.internal.TracedDecisionOperations`

Common operations:
- `addModification(Value traced, String explanation) -> Value`
- `toAuthorizationDecision(Value traced) -> AuthorizationDecision`
- Other convenience methods for interceptors

### Step 3.3: Update/simplify TracedDecision record

File: `io.sapl.api.pdp.internal.TracedDecision`

Simplify to wrap the Value:
```java
public record TracedDecision(Value value) {
    public AuthorizationDecision authorizationDecision() { ... }
    public Value trace() { ... }
    public TracedDecision modified(AuthorizationDecision decision, String explanation) { ... }
}
```

### Step 3.4: Update DynamicPolicyDecisionPoint

Modify `decideTraced()` flow:
1. Evaluate documents (each returns Level 1 or 2 traced decision)
2. Combine at PDP level
3. Build TracedPdpDecision with all metadata
4. Wrap in TracedDecision record

### Step 3.5: Update PDP-level combining

If PDP uses combining algorithms differently from policy sets:
- Ensure document-level traces are preserved
- Build correct PDP trace structure

### Step 3.6: Remove DecisionMetadata POJO

The `DecisionMetadata` record is replaced by trace in Value form.
Remove or deprecate once migration complete.

### Step 3.7: Tests for Level 3

- PDP builder produces correct structure
- Full flow from subscription to traced decision
- Modifications append correctly
- AuthorizationDecision extraction works
- Integration with interceptor chain

---

## Post-Implementation

### Cleanup

- Remove deprecated POJOs if any
- Update documentation
- Review for dead code

### Benchmarking

- Measure trace overhead vs non-traced path
- Profile memory usage
- Test with high-throughput scenarios

### Integration

- Verify interceptor chain works with new TracedDecision
- Test with logging interceptors
- Test with IDE/development tools if available

---

## File Checklist

### New Files (Level 1)
- [ ] `io.sapl.api.pdp.internal.TraceFields`
- [ ] `io.sapl.api.pdp.internal.TracedPolicyDecision`

### New Files (Level 2)
- [ ] `io.sapl.api.pdp.internal.TracedPolicySetDecision`

### New Files (Level 3)
- [ ] `io.sapl.api.pdp.internal.TracedPdpDecision`
- [ ] `io.sapl.api.pdp.internal.TracedDecisionOperations`

### Modified Files (Level 1)
- [ ] `SaplCompiler.java` - modify `compileDecisionExpression()` to wrap result with trace building
- [ ] `CompiledPolicy.java` - possibly add entitlement field (evaluate during implementation)

### Modified Files (Level 2)
- [ ] `CombiningAlgorithmCompiler.java` - receive TracedPolicyDecision from decisionExpression, buffer and compose into TracedPolicySetDecision

### Modified Files (Level 3)
- [ ] `DynamicPolicyDecisionPoint.java` - build TracedPdpDecision
- [ ] `TracedDecision.java` - simplify to wrap Value

### Files to Remove/Deprecate
- [ ] `DecisionMetadata.java` (after Level 3)

---

## Open Questions

1. **Single-policy document structure:** Option A (flat) or B (wrapped)?
   → Decide during Level 2 implementation based on code clarity

2. ~~**Where does policy evaluation with tracing happen?**~~
   → RESOLVED: In `CombiningAlgorithmCompiler` where policies are evaluated and combined.
   The compiler has access to policy name (from CompiledPolicy) and evaluates decisionExpression
   to get Value with metadata. Trace is built there.

3. **Stream path complexity:** How to efficiently track per-policy traces in combineLatest?
   → May need wrapper structure to associate policy name with trace.
   Each policy Flux emits Values. Need to track "latest traced decision per policy"
   and rebuild set trace on each emission.

4. **Error in target expression:** How to represent in trace?
   → TracedPolicyDecision with INDETERMINATE decision and error in errors array.
   Entitlement field tells what the policy intended.
