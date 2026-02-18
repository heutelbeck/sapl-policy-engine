---
layout: default
title: Attribute Finders
has_children: true
parent: SAPL Reference
nav_order: 250
has_toc: false
---

## Attribute Finders

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
    resource.type == "patient_record" & action == "read";
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
   resource.type == "patient_record";
   subject.username.<user.profile>.function == "doctor";
```

The first statement filters by action and resource type, fast checks that don't require external data.

The policy assumes that the user's function is not provided in the authorization subscription but is stored in the user's profile. The second statement accesses the attribute `user.profile` (using an attribute finder step `.<finder.name>`) to retrieve the profile of the user with the username provided in `subject.username`. The fetched profile is a JSON object with a property named `function`. The expression compares it to `"doctor"`.

### Streaming Attributes

In many scenarios, authorization decisions should update automatically when data changes. SAPL's streaming model enables this without polling or manual refresh.

Consider access control based on work shifts:

```sapl
policy "read patient records during business hours"
permit
    resource.type == "patient_record" & action == "read";
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
    resource.type == "patient_record" & action == "read";
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

> This example assumes a custom `schedules` PIP that provides shift information. See [Custom Attribute Finders](../6_6_CustomAttributeFinders/) for implementing custom PIPs.

Traditional access control systems make one-time decisions. SAPL maintains continuous authorization that adapts to changing conditions - time passing, data updates, or policy changes - all without the PEP needing to re-request decisions.

### Built-in and Custom PIPs

The examples above use built-in PIPs like `time.now` and `time.localTimeIsBetween`. SAPL includes a library of built-in PIPs for common authorization needs: time and date functions, string manipulation, filtering, JSON processing, and more.

**SAPL's plugin architecture enables domain-specific authorization.** Organizations can implement custom PIPs as plugins to integrate their domain-specific data sources and business logic into authorization policies:

- HR systems and organizational hierarchies
- Scheduling and calendar systems
- Compliance and regulatory engines
- Real-time monitoring and metrics
- Industry-specific business rules
- Any database, API, or system relevant to authorization decisions

Custom PIPs are implemented as plugins to the PDP, requiring no modifications to SAPL core. The `schedules` PIP in the shift example above would be a custom PIP specific to that organization's workforce management system.

This plugin architecture enables organizations to create sophisticated authorization logic tailored to their exact business requirements. A hospital can implement medical protocol PIPs, a bank can implement trading rule PIPs, and a manufacturing facility can implement safety certification PIPs - all using the same SAPL policy language.

Implementation details for custom PIPs are covered in [Custom Attribute Finders](../6_6_CustomAttributeFinders/).

### Structuring Policy Conditions

All conditions after `permit` or `deny` are body statements. While there is no grammar-level distinction between them, it is good practice to put fast, local checks first and slower PIP-based lookups later. This helps the engine skip expensive external calls early when simple conditions already determine the outcome.

**Recommended ordering:**
1. **Fast local checks first**: Resource type, action, simple equality checks on subscription attributes. These evaluate instantly and can short-circuit the rest.
2. **PIP-based lookups later**: Attribute finder expressions (`<finder.name>`) may involve network calls or database queries and should only run when the fast checks have already passed.

Note that `&`/`|` and `&&`/`||` are at different precedence levels. `&&` and `||` bind less tightly than `&` and `|`, allowing you to group conditions without parentheses. In future releases, these operator pairs will also select between different asynchronous evaluation strategies.

> **Policy sets** have a dedicated `FOR` clause that acts as a target expression for filtering which policies in the set are evaluated. See [Policy Sets](../2_5_PolicySets/) for details.

### Functions vs. Attributes

SAPL expressions can call both **functions** and **attributes**. They serve fundamentally different purposes:

**Functions** are pure mappings. Given the same input, a function always returns the same output. They are synchronous, side-effect-free, and evaluated inline. Functions use the dot-call syntax familiar from most languages:

```sapl
time.dayOfWeek(<time.now>)
filter.blacken(subject.creditCardNumber, 0, 12)
```

**Attributes** (accessed through PIPs) are fundamentally different. They represent external, potentially changing state. An attribute lookup may involve a network call, a database query, or a subscription to a live data stream. Attributes are:

- **Asynchronous**: They may take time to resolve, involving I/O operations.
- **Non-idempotent**: The same attribute may return different values at different times (the current time, a user's role after a promotion, a sensor reading).
- **Subscription-based**: Attributes return reactive streams. The PDP subscribes to them, and the PIP pushes new values whenever the underlying data changes.

This distinction is why attributes use a dedicated syntax (angle brackets `<...>`) rather than the function call syntax. The angle brackets signal to both the reader and the engine that this expression involves external I/O and may trigger ongoing subscriptions.

### Attribute Finder Syntax

SAPL provides two forms of attribute access:

**Environment attributes** use angle brackets without a base value. They access global or contextual data:
```sapl
<time.now>
<time.localTimeIsBetween("08:00", "18:00")>
```

**Entity attributes** use a dot followed by angle brackets, chaining from a base value. The value on the left is passed to the PIP as context:
```sapl
subject.username.<user.profile>
"42".<traccar.position>
```

A **head attribute finder** syntax `|<finder.name>` is also available for pipeline-style chaining. Attribute finders also accept **parameters** in parentheses and **options** in square brackets that control the stream resilience pipeline (timeout, retry, polling).

### Attribute Finder Reference

Attribute finders are used to receive attributes that are not included in the authorization subscription context from external PIPs. Just like in `subject.age`, the selection step `.age` selects the attribute `age`s value, `subject.<user.age>` could be used to fetch an `age` attribute which is not included in the `subject` but can be obtained from a PIP named `user`.

Attribute finders are organized in libraries as well and follow the same naming conventions as functions, including the use of imports. An attribute finder library constitutes a PIP (e.g., `user`) and can contain any number of attributes (e.g., `age`). They are called by a selection step applied to any value, e.g., `subject.<user.age>`. The attribute finder step receives the previous selection result (in the example: `subject`) and returns the requested attribute.

The concept of attribute finders can be used in a flexible manner: There may be finders that take an object (like in the example above, `subject.<user.age>`) as well as attribute finders which expect a primitive value (e.g., `subject.id.<user.age>` with `id` being a number). In addition, attribute finders may also return an object which can be traversed in subsequent selection steps (e.g., `subject.<user.profile>.age`). It is even possible to join multiple attribute finder steps in one expression (e.g., `subject.<user.profile>.supervisor.<user.profile>.age`).

Optionally, an attribute finder may be supplied with a list of parameters: `x.<finder.name(p1,p2,…​)>`. Also, here nesting is possible. Thus `x.<finder.name(p1.<finder.name2>,p2,…​)>` is a working construct.

Furthermore, attribute finders may be used without any leading value `<finder.name(p1,p2,…​)>`. These are called environment attributes.

The way to read a statement with an attribute finder is as follows. For `subject.<groups.membership("studygroup")>` one would say "get the attribute `group.membership` with parameter `"studygroup"` of the subject".

Attribute finders often receive information from external data sources such as files, databases, or HTTP requests which may take a certain amount of time. Therefore, they must not be used in a target expression. Attribute finders can access environment variables.

### Head Attribute Finder

The **head attribute finder** syntax `|<finder.name>` applies an attribute finder to the result of the preceding expression in a pipeline-like fashion. This is useful when chaining transformations where the intermediate result should be passed to a PIP.

### Parameters

Parameters are additional inputs passed to a PIP inside parentheses. They are positional and can be any SAPL expression, including literals, variables, or function results.

What parameters mean depends on the PIP. Common uses include temporal boundaries, configuration objects, and behavior modifiers:

```sapl
<time.now(5000)>                              // update interval in milliseconds
<time.localTimeIsBetween("09:00", "17:00")>   // start and end times
<http.get(requestSettings)>                   // request configuration object
topic.<mqtt.messages(1)>                      // QoS level
```

PIPs can be overloaded by parameter count. For example, `<time.now>` returns the current time with a default update interval, while `<time.now(5000)>` does the same with a 5-second interval.

### Options

Options control the **stream infrastructure** that wraps every attribute lookup. They are specified in square brackets after the parameters and must be a JSON object:

```sapl
<http.get(request) [{ "initialTimeOutMs": 500, "retries": 5 }]>
```

Options are not passed to the PIP itself. Instead, they configure how the engine handles the reactive stream that the PIP returns. The available option fields are:

| Option             | Default | Purpose                                                     |
|--------------------|---------|-------------------------------------------------------------|
| `initialTimeOutMs` | `3000`  | Timeout for the first value. Emits `undefined` if exceeded. |
| `pollIntervalMs`   | `30000` | Re-subscription interval when the PIP stream completes.     |
| `retries`          | `3`     | Retry attempts with exponential backoff on errors.          |
| `backoffMs`        | `1000`  | Initial backoff delay between retries.                      |
| `fresh`            | `false` | If `true`, bypasses the shared stream cache.                |

### Priority Chain

Options follow a three-level priority chain:

1. **Policy-level options** (in square brackets in the policy expression) have the highest priority.
2. **PDP-level defaults** (configured in `pdp.json` under `variables.attributeFinderOptions`) override built-in defaults.
3. **Built-in defaults** (listed in the table above) apply when no override is specified.

This allows operators to tune stream behavior globally without modifying policies, while individual policies can override when needed.

### Attribute Broker

The attribute broker mediates between policies and PIPs, managing stream lifecycle and resilience.

#### For Policy Authors

- **Stream sharing:** Identical attribute accesses share one PIP connection, reducing load on external systems. The `fresh` option bypasses this cache when a policy needs an independent stream.
- **The resilience pipeline:** Every attribute lookup is automatically wrapped in a pipeline: timeout, retry with exponential backoff, poll on stream completion, and error-to-undefined conversion. Each option (`initialTimeOutMs`, `retries`, `backoffMs`, `pollIntervalMs`) controls one stage.

#### For PDP Operators

- **Global option defaults:** Configure default attribute finder options in `pdp.json` under `variables.attributeFinderOptions` to tune stream behavior across all policies without modifying them.
- **Grace period:** After the last subscriber disconnects, the broker keeps PIP connections alive for 3 seconds. This prevents unnecessary reconnections when policies are rapidly re-evaluated.
- **Hot-swapping:** PIPs can be loaded and unloaded at runtime. Active streams automatically reconnect to newly loaded PIPs.

#### For PIP Developers

- **Resolution priority:** When the broker receives an attribute request, it resolves the PIP using: exact parameter match, then varargs match, then repository fallback, then error.
- **Automatic resilience wrapping:** PIP authors implement only the data-fetching logic. The broker automatically wraps PIP streams with the timeout/retry/polling pipeline.
- **Collision detection:** If multiple PIPs register the same fully qualified attribute name, the broker detects the collision at load time and logs a warning.

> **Planned content.** This page will be expanded with implementation details, configuration examples, and diagrams.
