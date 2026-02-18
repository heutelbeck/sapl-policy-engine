---
layout: default
title: Overview
#permalink: /reference/Overview/
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 101
---

## Overview

> For a guided introduction to policy structure with worked examples, see [Structure of a SAPL Policy](../1_3_Structure_of_a_SAPL-Policy/).

SAPL knows two types of documents: policy sets and policies. The decisions of the PDP are based on all documents published in the policy store of the PDP. A policy set contains an ordered set of connected policies.

### Policy Structure

A SAPL policy consists of optional **imports**, optional **schemas** for authorization subscription elements, a **name**, an **entitlement** specification, an optional **body** with conditions and variable assignments, and optional sections for **obligation**, **advice**, and **transformation**.

```sapl
import filter.blacken                                   // (1)

subject schema aSubjectSchema                           // (2)

policy "test_policy"                                    // (3)
permit                                                  // (4)
    subject.id == "anId" | action == "anAction";        // (5)
    var variable = "anAttribute";                       // (5)
    subject.attribute == variable;                      // (5)
    var foo = true schema {"type": "boolean"};          // (6)
obligation
    "logging:log_access"                                // (7)
advice
    "logging:inform_admin"                              // (8)
transform
    resource.content |- blacken                         // (9)
```

**1.** Imports (optional)

**2.** Schemas (optional)

**3.** Name

**4.** Entitlement

**5.** Body (optional): conditions and variable assignments, each ending with `;`

**6.** Schema annotation on a variable (optional, for editor code completion)

**7.** Obligation (optional)

**8.** Advice (optional)

**9.** Transformation (optional)


### Policy Set Structure

A SAPL policy set contains optional **imports**, a **name**, a **combining algorithm**, an optional **target expression**, optional **variable definitions**, and a list of **policies**. The following example shows a simple policy set with two policies:

```sapl
import filter.blacken                                   // (1)

set "test_policy_set"                                   // (2)
priority deny or deny                                   // (3)
for resource.type == "aType"                            // (4)
var dbUser = "admin";                                   // (5)

    policy "test_permit_admin"                          // (6)
    permit subject.function == "admin"

    policy "test_permit_read"                           // (7)
    permit action == "read"
    transform resource |- blacken
```

**1.** Imports (optional)

**2.** Name

**3.** Combining Algorithm

**4.** Target Expression (optional)

**5.** Variable Assignments (optional)

**6.** Policy 1

**7.** Policy 2
