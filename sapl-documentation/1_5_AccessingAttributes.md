---
layout: default
title: Accessing Attributes
#permalink: /reference/Accessing-Attributes/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 5
---

> **Introduction Series:** [1. Overview](../1_1_Introduction/) • [2. Subscriptions](../1_2_AuthorizationSubscriptions/) • [3. Policy Structure](../1_3_Structure_of_a_SAPL-Policy/) • [4. Decisions](../1_4_AuthorizationDecisions/) • **5. Attributes** • [6. Getting Started](../1_6_GettingStarted/)

## Accessing Attributes

### External Attributes

When a PEP constructs an authorization subscription, it includes information available in the application's current context: the authenticated user (subject), the requested operation (action), the target resource, and contextual data (environment). This represents what the application **knows at that moment**.

However, policies often require information that isn't readily available to the PEP:
- A user's department membership (stored in HR systems)
- Security clearance levels (maintained in identity management and as document metadata)
- Current work schedules (managed by scheduling systems)
- Real-time conditions (current time, system status, resource availability)

**Policy Information Points (PIPs)** bridge this knowledge gap by fetching attributes from external sources on demand.

### Static vs. Streaming Attributes

The authorization subscription provides a **snapshot** of what the PEP knows when making the request. Once sent, these attributes don't change unless the PEP creates a new subscription. This works well for stable data like usernames or resource identifiers, but many authorization decisions depend on **dynamic conditions** that change over time: current time, system status, metrics, etc.

Because the authorization protocol is based on the PDP pushing decisions to the PEP (not the PEP pushing updated attributes), **dynamic attributes must come from PIPs**. This is why time-based policies use `<time.now>` rather than expecting the PEP to include timestamps - the PDP needs to access time continuously, not just at subscription creation.

PIPs enable both:
1. **Bridging knowledge gaps**: Fetching data the PEP doesn't have
2. **Enabling streaming policies**: Providing dynamic attributes that update over time

Consider a basic policy that checks attributes from the authorization subscription:

```sapl
policy "compartmentalize read access by department"
permit
    resource.type == "patient_record" & action == "read"
where
    subject.role == "doctor";
    resource.department == subject.department;
```

This policy works if the authorization subscription includes both `subject.role` and `subject.department`. But what if that information isn't in the subscription?

### External Attribute Access

In many cases, the PEP doesn't know the specifics of access policies and doesn't have access to all information required for authorization decisions. When a user's role or department is stored in an external system (user directory, HR database, etc.), policies can use PIPs to fetch this data.

In natural language, a suitable policy could be *"Permit doctors to read data from any patient."* The policy addresses the profile attribute of the subject, stored externally. SAPL allows expressing this policy as follows:

*Introduction - Sample Policy 2*

```sapl
 policy "doctors read patient data"
 permit
   action == "read" &
   resource.type == "patient_record"
 where
   subject.username.<user.profile>.function == "doctor";
```

In *line 4*, the target expression filters by action and resource type - fast checks that don't require external data.

The policy assumes that the user's function is not provided in the authorization subscription but is stored in the user's profile. Accordingly, *line 6* accesses the attribute `user.profile` (using an attribute finder step `.<finder.name>`) to retrieve the profile of the user with the username provided in `subject.username`. The fetched profile is a JSON object with a property named `function`. The expression compares it to `"doctor"`.

### Streaming Attributes

In many scenarios, authorization decisions should update automatically when data changes. SAPL's streaming model enables this without polling or manual refresh.

Consider access control based on work shifts:

```sapl
policy "read patient records during business hours"
permit
    resource.type == "patient_record" & action == "read"
where
    subject.role == "doctor";
    resource.department == subject.department;
    <time.localTimeIsBetween("08:00:00", "18:00:00")>;
```

The `<time.localTimeIsBetween(...)>` attribute is evaluated fresh each time the policy runs. More importantly, **it streams**: When a doctor is granted access at 17:59, the PEP receives PERMIT and maintains the connection. At exactly 18:00:01, the PDP automatically pushes a new DENY decision to the PEP - without any polling or manual refresh. The PEP can then terminate the session or deny further operations.

This is **Attribute Stream-based Access Control (ASBAC)** - policies respond to changing conditions in real-time.

#### Composing Streaming Attributes

PIPs can be composed for more sophisticated scenarios:

```sapl
policy "doctors read records during assigned shift"
permit
    resource.type == "patient_record" & action == "read"
where
    subject.role == "doctor";
    resource.department == subject.department;
    var currentDay = time.dayOfWeek(<time.now>);
    var todaysShift = subject.employeeId.<schedules.shifts(currentDay)>;
    <time.localTimeIsBetween(todaysShift.start, todaysShift.end)>;
```

This policy:
1. Gets the current day of the week from `<time.now>` (a built-in streaming PIP)
2. Uses that to fetch the doctor's shift schedule for today from a custom `schedules` PIP
3. Checks if the current time is within the shift window

All of these attributes stream - when the clock crosses a shift boundary, or when shift schedules are updated in the scheduling system, the PDP automatically sends new decisions to the PEP.

> This example assumes a custom `schedules` PIP that provides shift information. See [Attribute Finders](../8_1_AttributeFinders/) for implementing custom PIPs.

Traditional access control systems make one-time decisions. SAPL maintains continuous authorization that adapts to changing conditions - time passing, data updates, or policy changes - all without the PEP needing to re-request decisions.

### Built-in and Custom PIPs

The examples above use built-in PIPs like `time.now` and `time.localTimeIsBetween`. SAPL includes a comprehensive library of built-in PIPs for common authorization needs: time and date functions, string manipulation, filtering, JSON processing, and more.

**SAPL's plugin architecture enables domain-specific authorization.** Organizations can implement custom PIPs as plugins to integrate their domain-specific data sources and business logic into authorization policies:

- HR systems and organizational hierarchies
- Scheduling and calendar systems
- Compliance and regulatory engines
- Real-time monitoring and metrics
- Industry-specific business rules
- Any database, API, or system relevant to authorization decisions

Custom PIPs are implemented as plugins to the PDP, requiring no modifications to SAPL core. The `schedules` PIP in the shift example above would be a custom PIP specific to that organization's workforce management system.

This plugin architecture enables organizations to create sophisticated authorization logic tailored to their exact business requirements. A hospital can implement medical protocol PIPs, a bank can implement trading rule PIPs, and a manufacturing facility can implement safety certification PIPs - all using the same SAPL policy language.

Implementation details for custom PIPs are covered in [Attribute Finders](../8_1_AttributeFinders/).

### Target Expression vs. Body

Attribute access using PIPs (expressions in angle brackets `<...>`) must be placed in the policy body (the `where` clause), not in the target expression. The reason is that the target expression is used for indexing policies efficiently and needs to be evaluated quickly. External attribute lookups may involve network calls or database queries, making them too slow for the target expression.

**Target expression rules:**
- Must use eager evaluation operators (`&`, `|`) instead of lazy operators (`&&`, `||`)
- Cannot contain PIP attribute lookups (`<finder.name>`)
- Should be fast to evaluate for efficient policy indexing
- Typically, checks resource type and action

**Body rules:**
- Can use lazy evaluation operators (`&&`, `||`) for efficiency
- Can access external attributes through PIPs
- Can contain complex conditional logic
- Can use streaming attributes that update over time
