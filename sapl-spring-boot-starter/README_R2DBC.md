# R2DBC Query Manipulation Constraint

Use `r2dbcQueryManipulation` in SAPL policy obligations to filter SQL query results, control which columns are returned, and apply SQL functions to column values.

## Setup

1. Add `@QueryEnforce` to your repository methods:

```java
@Repository
public interface PersonRepository extends R2dbcRepository<Person, Integer> {

    @QueryEnforce(action = "findAll", subject = "{\"username\": #{principal.username}}")
    Flux<Person> findAll();
}
```

2. Write policies that return `r2dbcQueryManipulation` obligations:

```sapl
policy "filter by role"
permit action == "findAll"
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["active = true"]
}
```

## Constraint Format

```json
{
    "type": "r2dbcQueryManipulation",
    "conditions": ["<SQL WHERE condition>", ...],
    "selection": {
        "type": "whitelist | blacklist",
        "columns": ["column1", "column2"]
    },
    "transformations": {
        "column": "SQL_FUNCTION"
    },
    "alias": "table_alias"
}
```

| Property          | Required | Description                                        |
|-------------------|----------|----------------------------------------------------|
| `type`            | Yes      | Must be `"r2dbcQueryManipulation"`                 |
| `conditions`      | Yes      | Array of SQL WHERE clause conditions               |
| `selection`       | No       | Column projection settings                         |
| `transformations` | No       | SQL functions to wrap columns with                 |
| `alias`           | No       | Table alias prefix for columns (useful with JOINs) |

## Conditions

Write conditions as SQL WHERE clause fragments. Multiple conditions are ANDed together.

```sapl
// Single condition
"conditions": ["active = true"]

// Multiple conditions (AND)
"conditions": [
    "active = true",
    "role = 'USER'"
]

// Dynamic from subject attributes
"conditions": ["department = '" + subject.department + "'"]

// With IN clause
"conditions": ["category IN (1, 2, 3)"]
```

### Supported Operators

| Operator     | Example                      |
|--------------|------------------------------|
| `=`          | `status = 'active'`          |
| `<>` or `!=` | `role <> 'admin'`            |
| `>`          | `age > 18`                   |
| `>=`         | `price >= 10`                |
| `<`          | `age < 65`                   |
| `<=`         | `stock <= 100`               |
| `LIKE`       | `name LIKE 'A%'`             |
| `NOT LIKE`   | `email NOT LIKE '%test%'`    |
| `IN`         | `category IN (1, 2, 3)`      |
| `BETWEEN`    | `age BETWEEN 18 AND 65`      |
| `EXISTS`     | `EXISTS (SELECT 1 FROM ...)` |

## Column Selection

Control which columns are returned using `selection`:

**Whitelist** - only return these columns:
```json
"selection": {
    "type": "whitelist",
    "columns": ["id", "name", "email"]
}
```

**Blacklist** - return all columns except these:
```json
"selection": {
    "type": "blacklist",
    "columns": ["password", "ssn", "salary"]
}
```

## Transformations

Apply SQL functions to column values. The function wraps the column in the SELECT clause.

```json
"transformations": {
    "firstname": "UPPER",
    "email": "LOWER"
}
```

This transforms `SELECT firstname, email FROM ...` into `SELECT UPPER(firstname), LOWER(email) FROM ...`.

Common SQL functions:
- `UPPER`, `LOWER` - case conversion
- `TRIM`, `LTRIM`, `RTRIM` - whitespace removal
- `SUBSTRING` - extract part of string
- `COALESCE` - null handling
- `ROUND`, `FLOOR`, `CEIL` - numeric functions

## Alias

When using JOINs or subqueries, use `alias` to prefix column names:

```json
{
    "type": "r2dbcQueryManipulation",
    "conditions": ["p.active = true"],
    "selection": {
        "type": "whitelist",
        "columns": ["firstname", "lastname"]
    },
    "alias": "p"
}
```

This prefixes columns with `p.`, producing `SELECT p.firstname, p.lastname FROM ...`.

## Examples

### Basic Filtering

```sapl
policy "active users only"
permit action == "findAll"
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["active = true"]
}
```

### Filter by User Attribute

```sapl
policy "filter by department"
permit action == "findAll"
where
    subject.department != null;
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["department = '" + subject.department + "'"]
}
```

### Dynamic IN Clause

```sapl
policy "category filter"
permit action == "findAll"
where
    subject.allowedCategories != [];
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["category IN " +
        standard.replace(standard.replace(
            standard.toString(subject.allowedCategories),
            "[", "("), "]", ")")]
}
```

### Hide Sensitive Columns

```sapl
policy "hide salary"
permit action == "findAll"
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["active = true"],
    "selection": {
        "type": "blacklist",
        "columns": ["salary", "ssn", "bank_account"]
    }
}
```

### Transform Column Values

```sapl
policy "uppercase names"
permit action == "findAll"
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["active = true"],
    "transformations": {
        "firstname": "UPPER",
        "lastname": "UPPER"
    }
}
```

### Combined Example

```sapl
policy "restricted view"
permit action == "findAll"
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["role = 'USER'"],
    "selection": {
        "type": "whitelist",
        "columns": ["id", "firstname", "lastname", "email"]
    },
    "transformations": {
        "email": "LOWER"
    }
}
```

### With Table Alias

```sapl
policy "join query filter"
permit action == "fetchWithDetails"
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["p.active = true"],
    "selection": {
        "type": "whitelist",
        "columns": ["firstname", "lastname"]
    },
    "alias": "p"
}
```

### Deny When No Scope

```sapl
set "access control"
first-applicable

policy "deny without scope"
deny action == "findAll"
where
    subject.dataScope == undefined ||
    subject.dataScope == [];

policy "permit with filter"
permit action == "findAll"
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["category IN (1, 2, 3)"]
}
```

## Troubleshooting

| Problem                                       | Check                                                                                                        |
|-----------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `AccessDeniedException: Unhandled Obligation` | Constraint format is wrong. Verify `type`, `conditions` is an array, `selection.type` is whitelist/blacklist |
| Query not filtered                            | Repository method is missing `@QueryEnforce`                                                                 |
| Columns not projected                         | Column names in `columns` must match entity field names exactly (case-sensitive)                             |
| Transformations not applied                   | Column must be in the SELECT clause (either from selection or original query)                                |
| Alias not working                             | Make sure the alias matches what you use in conditions                                                       |
