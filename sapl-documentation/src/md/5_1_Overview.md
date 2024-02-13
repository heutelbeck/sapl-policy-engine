---
layout: default
title: Overview
#permalink: /reference/Overview/
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 2
---

## Overview

SAPL knows two types of documents: Policy sets and policies. The decisions of the PDP are based on all documents published in the policy store of the PDP. A policy set contains an ordered set of connected policies.

### Policy Structure

A SAPL policy consists of optional **imports**, optional **schemas** for authorization subscription elements, a **name**, an **entitlement** specification, an optional **target expression**, an optional **body** with one or more statements, and optional sections for **obligation**, **advice**, and **transformation**.
An example of a simple policy is:

Sample SAPL Policy

```java
import filter as filter (1)
subject schema aSubjectSchema (2)

policy "test_policy" (3)
permit <4>
    subject.id == "anId" | action == "anAction" (5)
where 
    var variable = "anAttribute";
    subject.attribute == variable; (6)
    var foo = true schema {"type": "boolean"} (7)
obligation
    "logging:log_access" (8)
advice
    "logging:inform_admin" (9)
transform
    resource.content |- filter.blacken (10)
```

**1.** Imports (optional)

**2.** Schemas (optional)

**3.** Name

**4.** Entitlement

**5.** Target Expression (optional)

**6.** Body (optional)

**7.** Schemas (optional)

**8.** Obligation (optional)

**9.** Advice (optional)

**10.** Transformation (optional)


### Policy Set Structure

A SAPL policy set contains optional **imports**, a **name**, a **combining algorithm**, an optional **target expression**, optional **variable definitions**, and a list of **policies**. The following example shows a simple policy set with two policies:

Sample SAPL Policy Set

```java
import filter.* (1)

set "test_policy_set" (2)
deny-unless-permit (3)
for resource.type == "aType" (4)
var dbUser = "admin";(5)

    policy "test_permit_admin" (6)
    permit subject.function == "admin"

    policy "test_permit_read" (7)
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
