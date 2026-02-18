---
layout: default
title: Imports
#permalink: /reference/Imports/
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 102
---

## Imports

SAPL provides access to functions and attribute finders organized in libraries. Library names typically consist of two segments separated by a period (e.g., `filter.blacken`, `time.now`). Within policies, you can reference these using their fully qualified names:

```sapl
filter.blacken(resource.secret)
subject.<user.profile>.department
```

Import statements let you use shorter names, making policies easier to read and write.

### Import Syntax

Each import statement starts with the keyword `import` and must specify a fully qualified function or attribute finder name:

```
import <library>.<name>
import <library>.<name> as <alias>
```

#### Basic Import

Import a function or attribute finder by its fully qualified name:

```sapl
import filter.blacken
import time.now
import user.profile
```

After importing, use the simple name directly:

```sapl
policy "example"
permit
    var dept = subject.<profile>.department;   // instead of subject.<user.profile>
    blacken(resource.secret);                  // instead of filter.blacken
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
    "audit" : { "user": blacken(subject.id) }
```

### See Also

- [Functions](7_1_Functions.md) - Using functions
- [Attribute Finders](8_1_AttributeFinders.md) - using attribute finders
