# MongoDB Query Manipulation Constraint

Use `mongoQueryManipulation` in SAPL policy obligations to filter MongoDB query results and control which fields are returned.

## Setup

1. Add `@QueryEnforce` to your repository methods:

```java
@Repository
public interface BookRepository extends ReactiveCrudRepository<Book, ObjectId> {

    @QueryEnforce(action = "findAll", subject = "{\"username\": #{principal.username}}")
    Flux<Book> findAll();
}
```

2. Write policies that return `mongoQueryManipulation` obligations:

```sapl
policy "filter by role"
permit action == "findAll"
obligation {
    "type": "mongoQueryManipulation",
    "conditions": ["{'admin': false}"]
}
```

## Constraint Format

```json
{
    "type": "mongoQueryManipulation",
    "conditions": ["<MongoDB query>", ...],
    "selection": {
        "type": "whitelist" | "blacklist",
        "columns": ["field1", "field2"]
    }
}
```

| Property     | Required | Description                                |
|--------------|----------|--------------------------------------------|
| `type`       | Yes      | Must be `"mongoQueryManipulation"`         |
| `conditions` | Yes      | Array of MongoDB query documents (strings) |
| `selection`  | No       | Field projection settings                  |

## Conditions

Write conditions as MongoDB query documents. Multiple conditions are ANDed together.

```sapl
// Single condition
"conditions": ["{'status': 'active'}"]

// Multiple conditions (AND)
"conditions": [
    "{'status': 'active'}",
    "{'price': {'$lte': 100}}"
]

// OR logic (inside condition)
"conditions": ["{'$or': [{'featured': true}, {'onSale': true}]}"]

// Dynamic from subject attributes
"conditions": ["{'category': {'$in': " + subject.allowedCategories + "}}"]
```

### Supported Operators

| Operator  | Example                             |
|-----------|-------------------------------------|
| `$eq`     | `{'status': {'$eq': 'active'}}`     |
| `$ne`     | `{'role': {'$ne': 'admin'}}`        |
| `$gt`     | `{'age': {'$gt': 18}}`              |
| `$gte`    | `{'price': {'$gte': 10}}`           |
| `$lt`     | `{'age': {'$lt': 65}}`              |
| `$lte`    | `{'stock': {'$lte': 100}}`          |
| `$in`     | `{'category': {'$in': ['a', 'b']}}` |
| `$nin`    | `{'status': {'$nin': ['deleted']}}` |
| `$regex`  | `{'name': {'$regex': '^A.*'}}`      |
| `$exists` | `{'email': {'$exists': true}}`      |
| `$or`     | `{'$or': [{...}, {...}]}`           |

## Field Projection

Control which fields are returned using `selection`:

**Whitelist** - only return these fields:
```json
"selection": {
    "type": "whitelist",
    "columns": ["id", "name", "category"]
}
```

**Blacklist** - return all fields except these:
```json
"selection": {
    "type": "blacklist",
    "columns": ["password", "ssn", "internalNotes"]
}
```

## Examples

### Filter by User Attribute

```sapl
policy "filter by department"
permit action == "findAll"
where
    subject.department != null;
obligation {
    "type": "mongoQueryManipulation",
    "conditions": ["{'department': '" + subject.department + "'}"]
}
```

### Hide Sensitive Fields

```sapl
policy "hide salary"
permit action == "findAll"
obligation {
    "type": "mongoQueryManipulation",
    "conditions": ["{'active': true}"],
    "selection": {
        "type": "blacklist",
        "columns": ["salary", "ssn"]
    }
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
    "type": "mongoQueryManipulation",
    "conditions": ["{'category': {'$in': " + subject.dataScope + "}}"]
}
```

## Troubleshooting

| Problem                                       | Check                                                                                                        |
|-----------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `AccessDeniedException: Unhandled Obligation` | Constraint format is wrong. Verify `type`, `conditions` is an array, `selection.type` is whitelist/blacklist |
| Query not filtered                            | Repository method is missing `@QueryEnforce`                                                                 |
| Fields not projected                          | Field names in `columns` must match domain object exactly (case-sensitive)                                   |
