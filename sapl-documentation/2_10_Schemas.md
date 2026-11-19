---
layout: default
title: Schemas
parent: The SAPL Policy Language
nav_order: 110
---

## Schemas for Authorization Subscriptions

SAPL allows predefined structure for authorization subscription elements using [JSON Schema](https://json-schema.org/) (2020-12 version). Schemas serve three purposes:

- Defining the contract between PEP and PDP for the structure of authorization subscriptions
- Enforcing that contract at evaluation time, making non-compliant subscriptions automatically inapplicable
- Enabling richer code completion in SAPL editors based on the known structure

### Schema Syntax

Schema statements are declared after any [imports](2_9_Imports.md) and before the policy or policy set. Each schema targets one subscription element:

```
<subscription-element> schema <expression>
<subscription-element> enforced schema <expression>
```

Where `<subscription-element>` is `subject`, `action`, `resource`, or `environment`, and `<expression>` is a SAPL expression evaluating to a JSON Schema object.

**Enforced vs. non-enforced:** Without `enforced`, the schema is used only by SAPL editors for code completion. With `enforced`, the engine implicitly adds schema validation to the document's applicability check. If an explicit target expression is also present, both the explicit target and the schema validation must hold for the document to be applicable.

**Multiple schemas:** A subscription element may have more than one schema statement. When enforcing, the element is valid if it satisfies at least one of the provided schemas.

**Restrictions:** Schema expressions must not contain attribute finder expressions (`<attribute.name>`) since schemas are evaluated at compile time without access to external data sources.

**External schema references:** If a schema uses `$ref` to reference other schemas, the engine resolves these from a PDP-level variable called `schemas`. This variable must be an array of JSON Schema objects, each with a `$id` field. PDP variables are part of the [PDP configuration](../2_2_PDPConfiguration/#variables) and are not to be confused with the `environment` object in the authorization subscription.

### Variable Schemas

Schemas can also be attached to variable definitions for IDE support. These schemas are not enforced at runtime but enable code completion for the variable's value:

```sapl
var account = resource.account schema {
    "type": "object",
    "properties": {
        "balance": { "type": "number" },
        "owner": { "type": "string" }
    }
};
```

Multiple schemas can be attached to a single variable, separated by commas:

```sapl
var data = resource.payload schema { "type": "object" }, { "type": "array" };
```

### Example

The following policy uses an enforced schema to ensure that the subject contains the expected fields:

```sapl
subject enforced schema {
    "type": "object",
    "required": ["username", "role"],
    "properties": {
        "username": { "type": "string" },
        "role": {
            "type": "string",
            "enum": ["admin", "user", "guest"]
        }
    }
}

policy "admin access"
permit
    subject.role == "admin";
```

A compliant authorization subscription:

```json
{
    "subject": { "username": "alice", "role": "admin" },
    "action": "read",
    "resource": "dashboard"
}
```

This subscription matches the schema (both required fields present, `role` is a valid enum value), so the policy is evaluated normally and returns `PERMIT`.

A non-compliant authorization subscription:

```json
{
    "subject": { "username": "bob" },
    "action": "read",
    "resource": "dashboard"
}
```

This subscription fails schema validation (missing required field `role`), so the document is not applicable regardless of whether the policy body would match. The engine returns `NOT_APPLICABLE`.

### Patterns in Schemas

The `pattern` and `patternProperties` keywords use the [regular expressions](../2_7_Expressions/#regular-expressions) of SAPL, written in the syntax of RE2. As the JSON Schema specification demands, a pattern matches if it matches any part of the text. Anchor it with `^` and `$` to match the whole text.

The JSON Schema specification names the regular expressions of ECMA-262 (JavaScript) for these keywords, and they know look-ahead, look-behind and backreferences. SAPL deliberately uses RE2 for schemas as well. Matching stays linear in time, although the subscription it validates is untrusted input. A pattern means the same in a schema as with the `=~` operator. And every SAPL engine accepts the same schemas. Schemas taken from other projects may therefore need to be rewritten.

A typical case is a password rule written with look-ahead:

```json
{
    "type": "string",
    "pattern": "^(?=.*[0-9])(?=.*[A-Z])(?=.*[a-z]).{12,}$"
}
```

Each look-ahead becomes a pattern of its own under `allOf`, and the length becomes `minLength`:

```json
{
    "type": "string",
    "minLength": 12,
    "allOf": [
        { "pattern": "[0-9]" },
        { "pattern": "[A-Z]" },
        { "pattern": "[a-z]" }
    ]
}
```

A negative look-ahead becomes `not`. Instead of `"pattern": "^(?!.*admin).*$"`, write `"not": { "pattern": "admin" }`.

A schema with a pattern that RE2 refuses, because it uses look-around, backreferences or other unsupported syntax, or because it is too large, never lets a subscription through:

- An enforced schema statement with such a pattern is refused when the policy is compiled, and the error names the reason. This also holds for a pattern in a branch that a subscription might never reach, and for a pattern in a schema of the `schemas` variable that the schema references with `$ref`. A schema statement without `enforced` is used by editors only and is not checked.
- The functions of the `jsonschema` library return an error naming the reason, never `true` and never a validation result.

The `format` keyword is an annotation and is not checked. `"format": "regex"` does not test whether a text is a valid pattern. Use `patterns.isValidRegex` for that.
