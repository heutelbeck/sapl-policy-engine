---
layout: default
title: Functions
##permalink: /reference/Functions/
has_children: true
parent: SAPL Reference
nav_order: 200
has_toc: false
---

## Functions

Functions can be used within SAPL expressions (basic function expressions). A function takes some inputs (called *arguments*) and returns an output value.

Functions are organized in function libraries. Each function library has a *name* consisting of one or more identifiers separated by periods `.` (e.g., `simple.string` or `filter`). The *fully qualified name* of a function consists of the library name followed by a period and the function name (e.g., `simple.string.append`).

Functions can be used in any part of a SAPL document, especially in the target expression. Therefore, their output should only depend on the input arguments, and they should not access external resources. Functions do not have access to environment variables.

SAPL ships with a standard function library providing some basic functions.