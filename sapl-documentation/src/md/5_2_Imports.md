---
layout: default
title: Imports
#permalink: /reference/Imports/
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 3
---

## Imports

SAPL provides access to functions or attribute finders stored in libraries. The names of those libraries usually consist of different parts separated by periods (e.g., `sapl.pip.http` - a library containing functions to obtain attributes through HTTP requests). In policy documents, the functions and finders can be accessed by their fully qualified name, i.e., the name of the library followed by a period (`.`) and the function or finder name, e.g., `sapl.pip.http.get`.

For any SAPL top-level document (i.e., a policy set or a policy that is not part of a policy set), any number of imports can be specified. Imports allow using a shorter name instead of the fully qualified name for a function or an attribute finder within a SAPL document. Thus, imports can make policy sets and policies easier to read and write.

Each import statement starts with the keyword `import`.

- **Basic Import**: A function or an attribute finder can be imported by providing its fully qualified name (e.g., `import sapl.pip.http.get`). It will be available under its simple name (in the example: `get`) in the whole SAPL document.
- **Wildcard Import**: All functions or attribute finders from a library can be imported by providing an asterisk instead of a function or finder name (e.g., `import sapl.pip.http.*`). All functions or finders from the library will be available under their simple names (in the example: `get`).
- **Library Alias Import**: All functions or attribute finders from a library can be imported by providing the library name followed by `as` and an alias, e.g., `import sapl.pip.http as rest`.

The SAPL document can contain any number of imports, e.g.

Sample Imports

```java
import sapl.pip.http.*
import filter.blacken
import simple.append

policy "sample"
...
```
