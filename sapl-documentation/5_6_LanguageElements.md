---
layout: default
title: Language Elements
#permalink: /reference/Language-Elements/
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 106
---

## Language Elements

The descriptions of the policy and policy set structure sometimes refer to language elements like identifiers and strings. These elements are explained in this section.

### Identifiers

Multiple elements in policies or policy sets require identifiers. E.g., a variable assignment expects an identifier after the keyword `var` - the name under which the assigned value will be available.

An identifier only consists of alphanumeric characters, `_` and `$`, and must not start with a number.

Valid Identifiers

```
a_long_name
aLongName
$name
_name
name123
```

Invalid Identifiers

```
a#name
1name
```

A caret `^` before the identifier may be used to avoid a conflict with SAPL keywords.

### Strings

Whenever strings are expected, the SAPL document must contain any sequence of characters enclosed by double quotes `"`. Any quote character occurring in the string must be escaped by a preceding `\`, e.g., `"the name is \"John Doe\""`.

### Comments

Comments are used to store information in a SAPL document which is only intended for human readers and has no meaning for the PDP. Comments are simply ignored when the PDP evaluates a document.

SAPL supports single-line and multi-line comments. A single-line comment starts with `//` and ends at the end of the line, no matter which characters follow.

Sample Single-Line Comment

```
policy "test" // a policy for testing
```

Multi-line comments start with `/*` and end with `*/`. Everything in between is ignored.

Sample Multi-Line Comment

```
policy "test"
/* A policy for testing.
Remove before deployment! */
```
