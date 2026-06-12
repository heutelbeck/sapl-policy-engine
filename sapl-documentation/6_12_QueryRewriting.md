---
layout: default
title: Query Rewriting
parent: SDKs and APIs
nav_order: 612
---

## Query Rewriting

Many applications want to filter results at the database, not in memory. SAPL supports this with query rewriting: a policy attaches a query-rewriting obligation, and the SAPL integration for your database intercepts the query your application issues, applies the obligation, and sends the rewritten query to the driver. Your data-access code does not change. You enforce on the calling method as usual, and the obligation does the rest.

Two backends are supported today: relational databases (SQL) and MongoDB. The obligation is identical across every SDK that supports a backend, so the same policy works unchanged on every SAPL Policy Enforcement Point (PEP) for that backend.

## How It Works

You apply enforcement (`@PreEnforce` in Spring, `@pre_enforce` in the Python SDKs, `#[PreEnforce]` in PHP) to the service or handler method as usual, and the policy attaches a query-rewriting obligation. While that decision is being enforced, the queries your code issues are intercepted, the obligation is applied, and the rewritten query is forwarded to the driver.

When no enforcement is active, the query passes through unchanged. There is no global filter. The obligation applies only inside the protected call, so the same repository or collection called outside an enforced method runs unfiltered.

Three rules hold for every backend and SDK:

- **Narrowing only.** The obligation can never widen the user's filter, only narrow it. It is combined with whatever the user already requested using `AND`, so if the user asked for `category = 'art'` and the obligation adds `tenant_id = 7`, the database returns only rows where both hold. The obligation never overrides a field the user is already filtering on.
- **Fail closed.** An unsupported or malformed obligation is rejected, and the decision is denied.
- **Register before you rely on it.** The integration must be registered before its obligations take effect. If a decision carries a query-rewriting obligation but the matching integration is not registered, SAPL denies the decision rather than silently ignoring the obligation. So registering the integration is mandatory wherever you write query-rewriting policies.

## The Obligation

### SQL: `sql:queryRewriting`

For relational backends the obligation's `type` is `sql:queryRewriting` (the alias `relational:queryRewriting` is accepted as a synonym). It carries three optional parts.

```jsonc
{
  "type":       "sql:queryRewriting",
  "criteria":   [],   // typed criteria, AND-joined at top level
  "conditions": [],   // raw SQL fragments, AND-joined
  "columns":    []    // SELECT projection narrowing
}
```

A typed criterion is a JSON object with `column`, `op`, and `value`.

```json
{ "column": "status", "op": "=", "value": "active" }
```

The supported operators are `=`, `!=`, `>`, `>=`, `<`, `<=`, `in` (with an array `value`), `like`, `notLike`, `isNull`, and `isNotNull`. The `isNull` and `isNotNull` operators do not need a `value`. Criteria can be grouped with `and` and `or`, and groups can be nested. Each top-level entry in the `criteria` array is combined with the others using `AND`.

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

The `columns` array narrows the `SELECT` projection. For `SELECT *` the obligation columns become the projection, and for an explicit projection they intersect with it. `columns` applies only to `SELECT`. For `UPDATE` and `DELETE` it is ignored.

### MongoDB: `mongo:queryRewriting`

For MongoDB the obligation's `type` is `mongo:queryRewriting`. The schema mirrors the SQL form, minus the `columns` projection feature.

```jsonc
{
  "type":       "mongo:queryRewriting",
  "criteria":   [],   // typed criteria, AND-joined at top level
  "conditions": []    // raw BSON fragments, AND-joined
}
```

The typed criteria language accepts the same operators as SQL except `like` and `notLike`. For pattern matching use the `conditions` escape hatch with `$regex`.

```json
{
  "type": "mongo:queryRewriting",
  "criteria": [ { "column": "tenantId", "op": "=", "value": 7 } ]
}
```

`conditions` carries raw MongoDB query fragments, each combined with the user's query inside a top-level `$and`.

```json
{ "conditions": [ "{ \"name\": { \"$regex\": \"^A\" } }" ] }
```

Condition fragments must be valid JSON (double-quoted), not MongoDB shell syntax. Every SAPL MongoDB integration parses them with a strict JSON parser, so a single-quoted or unquoted fragment is rejected (and the decision denied) identically everywhere. This is what lets a `mongo:queryRewriting` obligation behave the same on Spring and in Python.

### Shared Semantics

- Typed criteria are added as extra conditions combined with the user's query using `AND`, so they never conflict with a field the user is already filtering on.
- `conditions` fragments are combined with the user's query inside a top-level `$and` (or `AND`-ed into the SQL `WHERE`). The original query is preserved.
- The obligation can only narrow access, never widen it.
- A malformed criterion, an unsupported statement, or (for MongoDB) a non-JSON condition causes the decision to be denied.
- **Portability across PEPs.** Because the obligation and its behaviour are identical across SDKs, the same obligation produces the same narrowing on every PEP for that backend. A `mongo:queryRewriting` obligation authored once works unchanged on the Spring MongoDB integration, the Python `sapl_pymongo` integration, the NestJS Mongoose integration, and the PHP Doctrine ODM integration. The same holds for `sql:queryRewriting` across the SQL integrations, with two integration caveats. The NestJS Prisma integration supports the typed `criteria` and `columns` but not the raw-SQL `conditions` escape hatch, since Prisma's `where` is structured rather than SQL. The PHP Doctrine ORM integration supports `criteria` and `conditions` but not `columns`, since a Doctrine query hydrates entities and cannot narrow its projection without changing the result shape.

## Integrations

### Spring (R2DBC and MongoDB)

The SAPL Spring Boot starter activates a transparent integration when it sees `R2dbcRepository` or `ReactiveMongoTemplate` on the classpath. It wraps `DatabaseClient` for R2DBC and `ReactiveMongoTemplate` for MongoDB. Every query path (derived queries, `@Query` methods, direct `databaseClient.sql(...)` or template calls) ultimately runs through the wrapped bean. You annotate the calling service method with `@PreEnforce`, and no repository annotations are needed.

Each integration has its own opt-out property, both default `true`.

```properties
# Disable the R2DBC integration
io.sapl.method-security.r2dbc-shim.enabled=false

# Disable the MongoDB integration
io.sapl.method-security.mongo-shim.enabled=false
```

### Python: SQLAlchemy

Use the `sapl_sqlalchemy` package with any of the Python SDKs (FastAPI, Flask, Tornado, Django on SQLAlchemy). Register the listener and the provider once at startup.

```python
from sapl_sqlalchemy import SqlQueryRewritingProvider, register_orm_listener
from sapl_fastapi import register_provider  # or the register_provider of your SDK

register_orm_listener()
register_provider(SqlQueryRewritingProvider())
```

`register_orm_listener()` attaches to the SQLAlchemy `Session` class, so it covers every session including `AsyncSession` through its sync-session proxy, and registers the integration so it can satisfy a `sql:queryRewriting` obligation. Until you call it, a decision carrying that obligation is denied.

The integration hooks into the `do_orm_execute` ORM event, which fires for every query a session runs. A `Select`, an ORM `Update`, and an ORM `Delete` get the authorised `WHERE` predicate added, and a column-typed select gets its projection narrowed. Raw `text()` run through the session, a set operation such as `UNION` combined with predicates, and a column projection against an entity-typed select are rejected, and the decision is denied.

Execution that bypasses the ORM session entirely (SQLAlchemy Core `engine.execute()`, a raw DBAPI cursor) never triggers the event, so no filter is applied. This is a fail-open path you must account for: once the integration is registered the obligation is accepted, so off-session access is left unfiltered rather than denied. Off-session database access means you own row-level security manually for that path.

### Python: Django ORM

For applications on Django's native ORM, `sapl_django` ships a Django-specific provider.

```python
from sapl_django import DjangoQueryRewritingProvider, register_orm_listener, register_provider

register_orm_listener()
register_provider(DjangoQueryRewritingProvider())
```

The provider translates `criteria` into Django `Q` objects, `conditions` into a raw `WHERE` via `add_extra`, and `columns` into `.only()`. It hooks into `SQLCompiler.execute_sql`, which fires for every query in an enforced call, including prefetch and cascade-delete selects against other models. A query is a target only when its model carries the columns the criteria reference (or the projection columns). Non-target queries pass through unchanged, so an unrelated model is never given a column it lacks.

A column projection through `.only()` defers the other fields rather than blocking them, and a deferred field still loads lazily on first access. Treat `columns` as a projection for efficiency, not as hard column-level access control. For hard column security, pair it with content filtering on the response.

### Python: MongoDB (PyMongo)

For applications using the PyMongo asynchronous driver (`AsyncMongoClient`), `sapl_pymongo` provides the MongoDB integration. PyMongo has no central hook for rewriting queries, so the integration works by wrapping each collection. Wrap each collection once at startup, which also registers the integration, and register the provider.

```python
from sapl_pymongo import MongoDbQueryRewritingProvider, wrap_async_collection
from sapl_fastapi import register_provider  # or the register_provider of your SDK

register_provider(MongoDbQueryRewritingProvider())
widgets = wrap_async_collection(database["widgets"])  # wraps and registers the integration
```

Use `wrap_collection` for a synchronous `Collection` (the blocking enforcement path) and `wrap_async_collection` for an `AsyncCollection`. The wrapper covers `find`, `find_one`, `aggregate`, `count_documents`, `update_*`, and `delete_*`. Each applies the obligation to the query before passing it to the driver. An aggregation pipeline cannot be narrowed by this obligation, so it is rejected (and the decision denied), as is a malformed condition.

Because wrapping the collection is what registers the integration, you cannot enable it without also installing the interception. A collection used without wrapping, or a raw `database.command(...)`, is not intercepted: that is the fail-open path you must account for, the MongoDB equivalent of off-session SQL access. Wrap every collection an enforced method may reach.

### NestJS: Mongoose

For NestJS applications on Mongoose, `@sapl/nestjs/mongoose` provides the MongoDB integration. Register the shim once at startup, apply the plugin to your schemas, and register the provider in your module.

```ts
import { registerMongooseShim, createSaplMongoosePlugin, MongoDbQueryRewritingProvider } from '@sapl/nestjs/mongoose';

registerMongooseShim();                          // advertise the obligation
mongoose.plugin(createSaplMongoosePlugin(cls));  // or schema.plugin(...) per schema; cls is the nestjs-cls ClsService
// add MongoDbQueryRewritingProvider to your SAPL module's providers
```

The plugin hooks Mongoose query middleware for `find`, `findOne`, `countDocuments`, `update*`, and `delete*`, applying the obligation to the filter before the driver runs it. An aggregation pipeline cannot be narrowed by this obligation, so it is rejected (and the decision denied), as is a malformed condition. The plugin reads the active enforcement plan from the request-scoped CLS context the `@PreEnforce` PEP populates, so no repository changes are needed. You annotate the calling service method with `@PreEnforce` as usual.

Until `registerMongooseShim()` runs, a decision carrying a `mongo:queryRewriting` obligation is denied. A schema without the plugin is not intercepted: that is the fail-open path you must account for. Apply the plugin to every schema an enforced method may reach.

### NestJS: Prisma

For NestJS applications on Prisma, `@sapl/nestjs/prisma` provides the SQL integration. Register the shim, extend your Prisma client, and register the provider.

```ts
import { registerPrismaShim, createSaplPrismaExtension, SqlQueryRewritingProvider } from '@sapl/nestjs/prisma';

registerPrismaShim();
const prisma = basePrismaClient.$extends(createSaplPrismaExtension(cls));  // cls is the nestjs-cls ClsService
// add SqlQueryRewritingProvider to your SAPL module's providers
```

The extension hooks Prisma's `$allOperations` for filter operations (`findMany`, `findFirst`, `count`, `aggregate`, `groupBy`, `updateMany`, `deleteMany`), AND-merging the obligation's `criteria` into the operation's `where` and narrowing `columns` to a `select`. Prisma's `where` is structured rather than SQL, so the `conditions` escape hatch cannot be lowered and is rejected (the decision denied). Policies targeting Prisma use typed `criteria`. A unique-key operation (`findUnique`, `update`, `delete`, `upsert`) cannot be safely AND-narrowed, so it is denied while an obligation is active. Use `findFirst`, `updateMany`, or `deleteMany` instead. Operations without a filter (`create`, `createMany`) pass through.

Until `registerPrismaShim()` runs, a decision carrying a `sql:queryRewriting` obligation is denied. A client used without the extension is not intercepted: that is the fail-open path you must account for. Extend every client an enforced method may reach.

### PHP: Doctrine (ORM and ODM)

For Symfony applications the `sapl/sapl-php` bundle integrates with Doctrine. It uses the Doctrine ORM `SQLFilter` for relational backends and the Doctrine ODM `BsonFilter` for MongoDB. Unlike the other integrations, which intercept the query your code issues, the Doctrine filters are pull-based. Doctrine calls the filter and AND-merges the returned predicate into the root entity, every join, and every subquery on its own. The integration contributes a narrowing predicate rather than rewriting a query string.

The bundle registers the providers automatically when the Doctrine packages are present. You register and enable the filter in your Doctrine configuration.

```yaml
# relational (Doctrine ORM)
doctrine:
    orm:
        filters:
            sapl_sql:
                class: Sapl\Doctrine\Orm\SaplSqlFilter
                enabled: true

# MongoDB (Doctrine ODM)
doctrine_mongodb:
    document_managers:
        default:
            filters:
                sapl_mongo:
                    class: Sapl\Doctrine\Odm\SaplBsonFilter
                    enabled: true
```

You annotate the calling service or controller method with `#[PreEnforce]` as usual. The filter applies only while that decision is being enforced and is inert otherwise. Both shims are PreEnforce-only.

The SQL filter honours `sql:queryRewriting` (and the `relational:queryRewriting` alias) with the typed `criteria` and the raw-SQL `conditions` escape hatch. It does not support the `columns` projection. A Doctrine ORM query hydrates entities, so narrowing the SELECT list would change the result shape, and an obligation carrying `columns` is rejected (the decision denied). This matches the Python SQLAlchemy integration, which likewise rejects a column projection against an entity-typed select. The Mongo filter honours `mongo:queryRewriting` with typed `criteria` and strict-JSON `conditions`. An aggregation pipeline cannot be narrowed and is rejected (the decision denied).

Until the filter is registered and enabled, a decision carrying the matching obligation is denied. Native SQL, a raw DBAL connection, or any read that bypasses the Doctrine filter is not intercepted. That is the fail-open path you must account for, the Doctrine equivalent of off-session access. Keep enforced reads on the ORM or ODM.
