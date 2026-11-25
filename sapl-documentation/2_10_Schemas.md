---
layout: default
title: Schemas
parent: The SAPL Policy Language
nav_order: 110
---

## Schemas for Authorization Subscriptions

SAPL uses a restricted [JSON Schema](https://json-schema.org/) profile to describe the structure of authorization subscription elements. Use Draft 2020-12 for new schemas. Schemas serve three purposes:

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

### Schema References

SAPL resolves references within the current schema, to built-in meta-schemas for the supported drafts, and to schemas explicitly supplied to the PDP. A URI identifies a schema. Resolving it never triggers a network request or opens a file or an arbitrary classpath resource.

For an enforced schema statement, supply additional schemas in the PDP configuration variable `schemas`, an array of objects identified by `$id`. The functions `jsonschema.isCompliantWithExternalSchemas` and `jsonschema.validateWithExternalSchemas` accept this array as their third argument. For example, `{"$ref":"https://example.org/address"}` can use a supplied schema whose `$id` is `https://example.org/address`, even when the PDP is offline. See [PDP configuration variables](../2_2_PDPConfiguration/#variables).

The following rules apply when authoring portable schemas:

- Put reusable subschemas in a location defined by the dialect, such as `definitions` in Drafts 4, 6 and 7 or `$defs` in Draft 2020-12, and reference them with a JSON Pointer such as `#/$defs/address`. A reference into an arbitrary JSON field such as `#/elsewhere` has implementation-dependent behavior. Draft 4 also treats `$defs` as an unknown keyword. If it contains an unresolved reference, one engine can accept the schema and the other can reject it.
- SAPL checks ordinary `$ref` targets when it prepares the schema, including targets named in unused definitions. Supply each referenced schema or remove the unused definition.
- A supplied schema inherits the main schema's dialect unless it declares its own `$schema`. For example, a Draft 4 main schema passes Draft 4 to its supplied schemas. A supplied schema that declares Draft 2020-12 uses its own dialect.
- In Draft 2019-09, use `"$recursiveRef": "#"` for dynamic recursion. Using a URL as the value of `$recursiveRef` produces a schema error. Use an ordinary `$ref` to name a supplied schema by its URI.

Both engines reject an unresolved reference such as `//example.invalid/denied`. The URI printed in the error message can differ between engines.

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

The `pattern` and `patternProperties` keywords use the [regular expressions](../2_7_Expressions/#regular-expressions) of SAPL in the syntax of the `regex` crate, not the ECMA-262 syntax the JSON Schema specification names, so matching stays linear in time. A pattern matches if it matches any part of the text. Anchor it with `^` and `$` to match the whole text.

A rule written with look-ahead is written as several patterns under `allOf` instead. `"pattern": "^(?=.*[0-9])(?=.*[A-Z]).{12,}$"` becomes:

```json
{
    "type": "string",
    "minLength": 12,
    "allOf": [{ "pattern": "[0-9]" }, { "pattern": "[A-Z]" }]
}
```

A schema with a pattern the regex engine refuses never lets a subscription through, even if the subscription never reaches the pattern. An enforced schema statement with such a pattern is refused when the policy is compiled, and the functions of the `jsonschema` library return an error. Both name the reason.

A text longer than the text limit that a pattern would search is an error of the validation, never a text that matches or does not match. A property name longer than the text limit anywhere in the validated value is an error as well, whether a pattern reaches the name or not.

The Java engine and the Rust engine differ for a schema of draft 6 or draft 7 with one of these assertions in a key of `patternProperties`: an ASCII word boundary (`(?-u:\b)`, `(?-u:\B)`, `(?-u:\b{start})`, `(?-u:\b{end})`), a half word boundary (`\b{start-half}`, `\b{end-half}`) or a line anchor of the mode `(?mR)`. The Java engine reads such a key like any other pattern. The Rust engine refuses the schema: its schema validator checks the keys of these two drafts as ECMA-262 expressions, and ECMA-262 cannot state these assertions.

Use Draft 2020-12 when a portable schema needs one of these assertions. For example, both engines accept the following schema and reject the instance `{"a": 1}` because the matching property has the schema `false`.

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "patternProperties": { "(?mR)^a$": false }
}
```

The `format` keyword is an annotation and is not checked. `"format": "regex"` does not test whether a text is a valid pattern. Use `patterns.isValidRegex` for that.
