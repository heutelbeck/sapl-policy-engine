---
layout: default
title: Imports
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 108
---

## Imports

SAPL provides access to functions and attribute finders organized in libraries. Within policies, you can reference these using their fully qualified names:

```sapl
filter.blacken(resource.secret)
subject.<user.profile>.department
```

Import statements let you use shorter names, making policies easier to read and write.

### Import Syntax

Import statements must appear at the beginning of a SAPL document, before any [schema statements](2_9_Schemas.md) and the policy or policy set.

Each import statement starts with the keyword `import` and must specify a fully qualified function or attribute finder name:

```
import <library>.<name>
import <library>.<name> as <alias>
```

All identifiers in an import statement -- library segments, function name, and alias -- follow the [identifier rules](2_6_Expressions.md#identifiers), including [reserved identifiers](2_6_Expressions.md#reserved-identifiers).

#### Basic Import

Import a function or attribute finder by its fully qualified name:

```sapl
import filter.blacken
import user.profile
```

After importing, use the simple name directly:

```sapl
policy "show account"
permit
    subject.<profile>.role == "teller";
transform
    resource |- { @.cardNumber : blacken(4) }
```

#### Aliased Import

Use `as` to provide an alternative name, useful when:
- Two libraries export functions with the same name
- You want a more descriptive name in context

```sapl
import time.now as currentTime
import clock.now as systemTime
import filter.blacken as redact
```

### Import Conflicts

Each imported name must be unique within a document. The compiler reports an error if you attempt to import the same name twice:

```sapl
import time.now
import clock.now      // Error: Import conflict: 'now' already imported
```

**Solution:** Use an alias for one of the imports:

```sapl
import time.now
import clock.now as systemNow   // OK
```

### Unresolved References

If you use a function or attribute finder without importing it or qualifying it fully, the compiler reports an error:

```sapl
policy "example"
permit
obligation
  blacken(data);   // Error: Unresolved reference 'blacken'
```

**Solutions:**
1. Add an import: `import filter.blacken`
2. Use the fully qualified name: `filter.blacken(data)`

### Complete Example

```sapl
import filter.blacken
import time.dayOfWeek
import user.roles

policy "weekday-access"
permit
    action == "read";
    var day = dayOfWeek(<time.now>);
    day in ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];
    "employee" in subject.<roles>;
obligation
    { "audit" : { "user": blacken(subject.id) } }
```

### See Also

- [Functions and Attribute Finders](2_7_FunctionsAndAttributes.md) - Conceptual model
- [Functions](3_0_Functions.md) - Built-in function library reference
- [Attribute Finders](4_0_AttributeFinders.md) - Built-in PIP library reference
