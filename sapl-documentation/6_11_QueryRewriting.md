---
layout: default
title: Query Rewriting
parent: SDKs and APIs
nav_order: 611
---

## Query Rewriting

Many applications want to filter results at the database, not in memory. SAPL supports this with query rewriting: a policy emits a query-rewriting obligation, and an SDK shim catches the query your application issues, applies the obligation, and forwards the rewritten query to the driver. Your data-access code does not change. You enforce on the calling method as usual; the obligation does the rest.

Two backends are supported today: relational databases (SQL) and MongoDB. The obligation contract is identical across every SDK that implements a backend, so the same policy works unchanged on every SAPL Policy Enforcement Point (PEP) for that backend.

> The obligation `type` strings are `sql:queryManipulation` and `mongo:queryManipulation`. That is the on-the-wire contract and does not change. "Query rewriting" is the name of the capability those obligations drive.

## How It Works

You apply enforcement (`@PreEnforce` in Spring, `@pre_enforce` in the Python SDKs) to the service or handler method as usual, and the policy attaches a query-rewriting obligation. While that decision is being enforced, the queries your code issues reach the shim, which fires a shim signal, applies the obligation, and forwards the rewritten query to the driver.

When no enforcement is active, the shim passes the query through unchanged. There is no global filter. The obligation only applies inside the protected call, so the same repository or collection called outside an enforced method runs unfiltered.

Three invariants hold for every backend and SDK:

- **Narrowing only.** The obligation can never widen the user's filter, only narrow it. It is AND-joined with whatever the user already requested, so if the user asked for `category = 'art'` and the obligation adds `tenant_id = 7`, the database returns rows where both hold. The obligation never overwrites a field the user is already filtering on.
- **Fail closed.** An unsupported or malformed obligation raises, which the enforcement layer treats as an obligation failure and denies the decision.
- **Admissibility.** A shim advertises that it can satisfy the obligation only once it is registered or wired. Until then the obligation is inadmissible and any decision carrying it fails closed by denying. Registering the shim is therefore mandatory, not optional, wherever you author query-rewriting policies.

## The Obligation Contract

### SQL: `sql:queryManipulation`

For relational backends the constraint type is `sql:queryManipulation` (the alias `relational:queryManipulation` is accepted as a synonym). The obligation carries three optional parts.

```jsonc
{
  "type":       "sql:queryManipulation",
  "criteria":   [],   // typed criteria, AND-joined at top level
  "conditions": [],   // raw SQL fragments, AND-joined
  "columns":    []    // SELECT projection narrowing
}
```

A typed criterion is a JSON object with `column`, `op`, and `value`.

```json
{ "column": "status", "op": "=", "value": "active" }
```

The supported operators are `=`, `!=`, `>`, `>=`, `<`, `<=`, `in` (with an array `value`), `like`, `notLike`, `isNull`, and `isNotNull`. The `isNull` and `isNotNull` operators do not need a `value`. Criteria can be grouped with `and` and `or`, and groups can be nested. Each top-level entry in the `criteria` array is AND-joined with the others.

```json
[
  { "column": "tenant_id", "op": "=", "value": 7 },
  { "or": [
    { "column": "owner_id",  "op": "=", "value": "alice" },
    { "column": "is_public", "op": "=", "value": true }
  ]}
]
```

The `conditions` array carries raw SQL fragments for features the typed language does not cover, such as `BETWEEN`, `EXISTS`, or vendor functions.

```json
{ "conditions": [ "created_at > CURRENT_TIMESTAMP - INTERVAL '7 days'" ] }
```

The `columns` array narrows the `SELECT` projection. For `SELECT *` the obligation columns become the projection; for an explicit projection they intersect with it. `columns` applies only to `SELECT`; for `UPDATE` and `DELETE` it is ignored.

### MongoDB: `mongo:queryManipulation`

For MongoDB the constraint type is `mongo:queryManipulation`. The schema mirrors the SQL provider, minus the `columns` projection feature.

```jsonc
{
  "type":       "mongo:queryManipulation",
  "criteria":   [],   // typed criteria, AND-joined at top level
  "conditions": []    // raw BSON fragments, AND-joined
}
```

The typed criteria language accepts the same operators as SQL except `like` and `notLike`. For pattern matching use the `conditions` escape hatch with `$regex`.

```json
{
  "type": "mongo:queryManipulation",
  "criteria": [ { "column": "tenantId", "op": "=", "value": 7 } ]
}
```

`conditions` carries raw MongoDB query fragments, each parsed and intersected with the user's query inside a top-level `$and`.

```json
{ "conditions": [ "{ \"name\": { \"$regex\": \"^A\" } }" ] }
```

Condition fragments must be valid JSON (double-quoted), not MongoDB shell syntax. Every SAPL MongoDB PEP parses them with a strict JSON parser, so a single-quoted or unquoted fragment is rejected (and fails closed) identically on every PEP. This is what makes a `mongo:queryManipulation` obligation behave the same on Spring and on the Python shim.

### Shared Semantics

- Typed criteria are AND-joined at the top level and wrapped so they never collide with a field the user query is already filtering on.
- `conditions` fragments are intersected with the user's query inside a top-level `$and` (or AND-ed into the SQL `WHERE`); the original query is preserved.
- The obligation can only narrow access, never widen it.
- A malformed criterion, an unsupported statement, or (for MongoDB) a non-JSON condition raises and denies.
- **Cross-PEP portability.** Because the contract and its semantics are identical across SDKs, the same obligation produces the same narrowing on every PEP for that backend. A `mongo:queryManipulation` obligation authored once works unchanged on the Spring MongoDB shim and on the Python `sapl_pymongo` shim.

## Integrations

### Spring (R2DBC and MongoDB)

The SAPL Spring Boot starter activates a transparent shim when it sees `R2dbcRepository` or `ReactiveMongoTemplate` on the classpath. It wraps `DatabaseClient` for R2DBC and `ReactiveMongoTemplate` for MongoDB; every dispatch path (derived queries, `@Query` methods, direct `databaseClient.sql(...)` or template calls) bottoms out at the wrapped bean. You annotate the calling service method with `@PreEnforce`; no repository annotations are needed.

Each shim has its own opt-out property, both default `true`.

```properties
# Disable the R2DBC shim
io.sapl.method-security.r2dbc-shim.enabled=false

# Disable the Mongo shim
io.sapl.method-security.mongo-shim.enabled=false
```

### Python: SQLAlchemy

Use the `sapl_sqlalchemy` package with any of the Python SDKs (FastAPI, Flask, Tornado, Django on SQLAlchemy). Register the listener and the provider once at startup.

```python
from sapl_sqlalchemy import SqlQueryManipulationProvider, register_orm_listener
from sapl_fastapi import register_provider  # or the register_provider of your SDK

register_orm_listener()
register_provider(SqlQueryManipulationProvider())
```

`register_orm_listener()` attaches to the SQLAlchemy `Session` class, so it covers every session including `AsyncSession` through its sync-session proxy, and advertises that the integration can satisfy a `sql:queryManipulation` obligation.

The shim hooks into the `do_orm_execute` ORM event, which fires for every query a session runs. A `Select`, an ORM `Update`, and an ORM `Delete` get the authorised `WHERE` predicate injected, and a column-typed select gets its projection narrowed. Raw `text()` run through the session, a set operation such as `UNION` combined with predicates, and a column projection against an entity-typed select are rejected and deny.

Execution that bypasses the ORM session entirely (SQLAlchemy Core `engine.execute()`, a raw DBAPI cursor) never triggers the event, so no filter is applied. This is a fail-open path you must account for: once the listener is registered the obligation is admissible, so off-session access is left unfiltered rather than denied. Off-session database access means the developer owns row-level security manually for that path.

### Python: Django ORM

For applications on Django's native ORM, `sapl_django` ships a Django-specific provider.

```python
from sapl_django import DjangoQueryManipulationProvider, register_orm_listener, register_provider

register_orm_listener()
register_provider(DjangoQueryManipulationProvider())
```

The provider lowers `criteria` into Django `Q` objects, `conditions` into a raw `WHERE` via `add_extra`, and `columns` into `.only()`. The cut point is `SQLCompiler.execute_sql`, which fires for every query in an enforced call, including prefetch and cascade-delete selects against other models. A query is a target only when its model carries the columns the criteria reference (or the projection columns); non-target queries pass through unchanged, so an unrelated model is never injected with a column it lacks.

A column projection through `.only()` defers the other fields rather than blocking them, and a deferred field still loads lazily on first access. Treat `columns` as a projection for efficiency, not as hard column-level access control; for hard column security, pair it with content filtering on the response.

### Python: MongoDB (PyMongo / Motor)

For applications using the PyMongo asynchronous driver (or Motor), `sapl_pymongo` provides the MongoDB shim. PyMongo has no central mutating query hook, so the cut point is a thin proxy over a collection's query methods. Wrap each collection once at startup, which also registers the shim, and register the provider with the planner.

```python
from sapl_pymongo import MongoDbQueryManipulationProvider, wrap_async_collection
from sapl_fastapi import register_provider  # or the register_provider of your SDK

register_provider(MongoDbQueryManipulationProvider())
widgets = wrap_async_collection(database["widgets"])  # wraps + registers the shim
```

Use `wrap_collection` for a synchronous `Collection` (the blocking enforcement path) and `wrap_async_collection` for an `AsyncCollection`. The proxy covers `find`, `find_one`, `aggregate`, `count_documents`, `update_*`, and `delete_*`; each discharges the obligation against the structured filter before delegating to the driver. An aggregation pipeline cannot be narrowed by this contract, so a pipeline intercept fails closed, as does a malformed condition.

Because wrapping the collection is what registers the shim, advertising the capability and installing the cut point are inseparable. A collection obtained without wrapping, or a raw `database.command(...)`, is not intercepted: that is the fail-open path you must account for, the MongoDB analogue of off-session SQL access. Wrap every collection an enforced method may reach.
