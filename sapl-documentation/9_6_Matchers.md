---
layout: default
title: Matchers
parent: Testing SAPL Policies
grand_parent: SAPL Reference
nav_order: 505
---

## Matchers

Matchers are used throughout the test DSL to match values in function arguments, attribute entity parameters, and decision assertions. They follow a consistent syntax.

### Value Matchers

Value matchers appear in function and attribute mock definitions:

| Matcher                   | Matches                        | Example                                          |
|---------------------------|--------------------------------|--------------------------------------------------|
| `any`                     | Any value                      | `function f(any) maps to true`                   |
| Literal value             | Exact match                    | `function f("hello") maps to true`               |
| `matching <type>`         | Any value of the given type    | `function f(matching text) maps to true`          |
| `matching <type> <value>` | Specific value with type check | `function f(matching text "hello") maps to true`  |

### Type Matchers

Type matchers check that a value is of a specific JSON type:

| Matcher            | Matches           |
|--------------------|-------------------|
| `matching text`    | Any string value  |
| `matching number`  | Any numeric value |
| `matching boolean` | Any boolean value |
| `matching object`  | Any JSON object   |
| `matching array`   | Any JSON array    |
| `matching null`    | Null value        |

### String Matchers

String matchers provide detailed text matching within `matching text ...` or in object/array `where` clauses:

| Matcher                                            | Description                         |
|----------------------------------------------------|-------------------------------------|
| `text "exact"`                                     | Exact string match                  |
| `text empty`                                       | Empty string                        |
| `text blank`                                       | Blank string (whitespace only)      |
| `text null`                                        | Null string                         |
| `text null-or-empty`                               | Null or empty                       |
| `text null-or-blank`                               | Null or blank                       |
| `text containing "sub"`                            | Contains substring                  |
| `text containing "sub" case-insensitive`           | Contains, ignoring case             |
| `text starting with "pre"`                         | Starts with prefix                  |
| `text starting with "pre" case-insensitive`        | Starts with, ignoring case          |
| `text ending with "suf"`                           | Ends with suffix                    |
| `text ending with "suf" case-insensitive`          | Ends with, ignoring case            |
| `text equal to "val" case-insensitive`             | Equals, ignoring case               |
| `text equal to "val" with compressed whitespace`   | Equals after normalizing whitespace |
| `text with regex "^[A-Z]+$"`                       | Matches regular expression          |
| `text with length 8`                               | Exact string length                 |
| `text containing stream "a", "b", "c" in order`   | Contains substrings in order        |

### Object Matchers

Object matchers verify JSON object structure within `where` clauses:

```sapltest
expect decision is permit, with obligation matching object where {
    "type" is text "logAccess" and "user" is text and "timestamp" is number
};
```

Each field specifies a key and a type matcher joined by `and`:

```sapltest
"fieldName" is <type matcher>
```

The type matcher can be any of: `text`, `text "value"`, `number`, `number 42`, `boolean`, `boolean true`, `null`, `object`, `array`.

### Array Matchers

Array matchers verify JSON array contents within `where` clauses:

```sapltest
expect decision is permit, with resource matching array where [text "a", text "b", number 42];
```

Each element position specifies a type matcher. The array must match the specified elements in order.

### Decision Matchers

Decision matchers are used in `expect decision ...` clauses:

| Matcher                                                         | Description                                              |
|-----------------------------------------------------------------|----------------------------------------------------------|
| `any`                                                           | Matches any decision                                     |
| `is permit`                                                     | Decision is PERMIT                                       |
| `is deny`                                                       | Decision is DENY                                         |
| `is indeterminate`                                              | Decision is INDETERMINATE                                |
| `is not-applicable`                                             | Decision is NOT_APPLICABLE                               |
| `with obligation`                                               | Decision contains at least one obligation                |
| `with obligation equals <value>`                                | Decision contains the exact obligation                   |
| `with obligation matching <type>`                               | Decision contains an obligation matching the type        |
| `with obligation containing key "k"`                            | Decision contains an obligation with key "k"             |
| `with obligation containing key "k" with value matching <type>` | Key "k" has a value matching the type                    |
| `with advice`                                                   | Decision contains at least one advice                    |
| `with advice equals <value>`                                    | Decision contains the exact advice                       |
| `with resource`                                                 | Decision contains a resource                             |
| `with resource equals <value>`                                  | Decision contains the exact resource                     |
| `with resource matching <type>`                                 | Decision contains a resource matching the type           |

Multiple decision matchers are separated by commas:

```sapltest
expect decision is permit, with obligation, with resource matching object;
```
