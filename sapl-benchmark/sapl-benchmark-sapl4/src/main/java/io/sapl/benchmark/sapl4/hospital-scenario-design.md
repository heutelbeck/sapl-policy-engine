# Hospital Scenario Design

## Purpose

Benchmark scenario designed to demonstrate the canonical policy index at scale.
The existing scaling scenarios (simple-N, complex-N, shared-N) have a fundamental
flaw: filler policies use unique predicates per policy (`action == "action-4217"`,
`resource == "resource-4217"`), creating zero predicate overlap. This is the
pathological worst case for the canonical index, which degenerates to a linear
scan plus overhead.

Real authorization systems have the opposite property: a small vocabulary of
predicates shared across many policies. A hospital with 10,000 policies might
use only 12 distinct actions, 10 resource types, and 9 roles. The canonical
index exploits this overlap to skip irrelevant policies at evaluation time.

## Domain

A hospital authorization system combining three access control paradigms:

**ReBAC (Relationship-Based Access Control):**
Staff membership hierarchy: Staff -> Team -> Department. Each department-scoped
policy uses `graph.transitiveClosureSet(staffGraph)` to verify the subject has
a transitive path to the target department. At compile time, `staffGraph` is a
PDP variable, so the closure is computed once and folded into a constant lookup
table.

**ABAC (Attribute-Based Access Control):**
- Subject attributes: `role` (one of 9 roles), `clearance` (1-4, determined by role)
- Resource attributes: `type` (one of 10 types), `department` (entity ID), `sensitivity` (1-4)
- Action: one of 12 actions

**IN-list policies (for unrolling testing):**
Multi-action permits like `action in ["read", "write", "create"]` that contain
elements appearing as explicit `action == "read"` in other policies. When the
compiler unrolls IN-lists, both forms produce identical `action == "read"`
predicates that the canonical index can share.

## Fixed Vocabulary

```
Roles (9):     attending, resident, nurse, labTech, pharmacist,
               admin, billing, auditor, researcher

Actions (12):  read, write, create, delete, prescribe, order,
               discharge, transfer, bill, audit, approve, override

Resources (10): PatientRecord, LabResult, Prescription, Imaging,
                BillingRecord, Schedule, CareNote, Referral, Consent, AuditLog
```

## Permission Matrix

Each role has a fixed set of (resourceType, allowedActions) pairs. The matrix
produces 32 unique (role, resourceType) combinations per department.

| Role | Resource Types | Actions per Type | Policy Type |
|------|----------------|------------------|-------------|
| attending | PatientRecord, LabResult, Prescription, Imaging, CareNote, Referral | 2-3 each | all IN-lists |
| resident | PatientRecord, LabResult, Prescription, Imaging, CareNote, Referral | 1-3 each | mix == and IN |
| nurse | PatientRecord, LabResult, Prescription, CareNote, Schedule | 1-3 each | mix == and IN |
| labTech | LabResult, Imaging | 3 each | IN-lists |
| pharmacist | Prescription, PatientRecord | 1-3 each | mix == and IN |
| admin | Schedule, BillingRecord, AuditLog | 1-4 each | mix == and IN |
| billing | BillingRecord, PatientRecord | 1-4 each | mix == and IN |
| auditor | AuditLog, PatientRecord, BillingRecord | 1-4 each | mix == and IN |
| researcher | PatientRecord, LabResult, Imaging | 1 each | all single == |

## Policy Types

### 1. Department-scoped permit (32 per department)

```sapl
policy "dept-0-attending-PatientRecord"
permit
    subject.role == "attending";
    resource.type == "PatientRecord";
    action in ["read", "write", "create"];
    resource.department == "dept_0";
    var closed = graph.transitiveClosureSet(staffGraph);
    closed[(subject.id)][("dept_0")] != undefined;
```

Dual check: `resource.department` ensures the resource belongs to the department,
`closed[(subject.id)][(dept)]` ensures the subject is a member.

### 2. Sensitivity deny (1 per department)

```sapl
policy "sensitivity-deny-dept-0"
deny
    resource.department == "dept_0";
    resource.sensitivity > subject.clearance;
```

Cross-cutting: applies to ALL roles and resource types within the department.
With PRIORITY_DENY combining, these override matching permit policies.

### 3. Emergency override (5 global)

```sapl
policy "emergency-PatientRecord"
permit
    subject.role == "emergency";
    resource.type == "PatientRecord";
    action in ["read", "write", "prescribe", "override"];
```

No department restriction. Action IN-list creates additional predicate overlap.

## Scaling

Total policies: 33 * numDepartments + 5

| n (departments) | Policies | Staff | Teams |
|-----------------|----------|-------|-------|
| 5 | 170 | 25 | 10 |
| 10 | 335 | 50 | 20 |
| 50 | 1,655 | 250 | 100 |
| 100 | 3,305 | 500 | 200 |
| 200 | 6,605 | 1,000 | 400 |
| 300 | 9,905 | 1,500 | 600 |

## Predicate Overlap Analysis

At n=100 (3,305 policies):

### action predicates

`action == "read"` appears in:
- 10 explicit single-action policies per department = 1,000 total
- 12+ multi-action IN-list policies per department (as element) = 1,200 total
- When unrolled: 2,200+ policies share `action == "read"`
- Without unrolling: only 1,000 policies share it (IN-lists are opaque)

`action == "write"` appears in:
- 0 explicit single-action policies (always paired with other actions)
- 10+ IN-list policies per department = 1,000 total
- Only visible to the index when unrolled

### resource.type predicates

`resource.type == "PatientRecord"` appears in:
- attending, resident, nurse, pharmacist, billing, auditor, researcher = 7 per department
- Plus 1 emergency global policy
- Total: 701 policies

`resource.type == "LabResult"` appears in:
- attending, resident, nurse, labTech, researcher = 5 per department
- Plus 1 emergency global policy
- Total: 501 policies

### subject.role predicates

`subject.role == "attending"` appears in: 6 per department = 600 total
`subject.role == "nurse"` appears in: 5 per department = 500 total
`subject.role == "researcher"` appears in: 3 per department = 300 total

### resource.department predicates

`resource.department == "dept_0"` appears in exactly 33 policies
(32 permit + 1 deny for that department). This is the most selective predicate.

## Index Effectiveness

For a request: attending in dept_5 reads a PatientRecord in dept_5

Without index (NAIVE): evaluates all 3,305 policies sequentially.

With canonical index:
1. `resource.department == "dept_5"` -> narrows to 33 candidates
2. `subject.role == "attending"` -> narrows to 7 candidates (6 permit + 1 deny)
3. `resource.type == "PatientRecord"` -> narrows to 2 candidates (1 permit + 1 deny)
4. `action == "read"` -> final filter

Expected speedup: ~1,000x fewer policy evaluations at n=100.

## Unrolling Impact

Without unrolling, the canonical index sees `action in ["read", "write", "create"]`
as an opaque predicate. It cannot determine that this policy matches
`action == "read"` without evaluating the full expression.

With unrolling, the compiler transforms this to:
`action == "read" || action == "write" || action == "create"`

Each disjunct is now an indexable predicate. The canonical index can:
- Match `action == "read"` against all policies that check for "read"
- Skip policies checking for "write" or "create" when the request action is "read"

At scale, this dramatically improves index selectivity for action predicates,
which are among the most commonly shared.

## Entity Graph

```
staff_X -> [team_Y, ...]     # primary team + cross-dept
team_Y   -> [dept_Z]    # exactly one department
dept_Z -> []                  # no parents
```

Cross-department membership: each staff member has p=0.05 probability of
belonging to a team in each other department. With n=100, a staff member
has ~5 cross-department team memberships on average.

Transitive closure: `closed[(subject.id)][(dept)]` is true when the subject
has a path Staff -> Team -> Department to that department. The closure is
precomputed at compile time via `graph.transitiveClosureSet(staffGraph)`.

## Subscriptions

500 random requests per seed:
- Subject: random staff member (id, role, clearance from generation)
- Action: uniform random from 12 actions
- Resource: random type, random department, random sensitivity 1-4

Decision mix: depends on entity graph and random request. Expected mix of
PERMIT (matching role/type/action/department), DENY (sensitivity block or
no matching permit), and NOT_APPLICABLE (wrong action for role).
