---
layout: default
title: Functions and Attribute Finders
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 107
---

## Functions and Attribute Finders

SAPL expressions can call **functions** and access **attributes**. Both extend what policies can express beyond simple JSON manipulation, but they serve fundamentally different purposes and have different runtime characteristics. Understanding this distinction is essential for writing correct and efficient policies.

For the expression syntax of function calls and attribute finder steps, see [Expressions](../2_6_Expressions/). This section covers the conceptual model: what functions and attributes are, why SAPL has both, and how streaming attributes enable continuous authorization.

### Functions

Functions are pure computations. Given the same input arguments, a function always returns the same output. They are:

- **Synchronous**: They execute immediately and return a value.
- **Deterministic**: Same inputs always produce the same output.
- **Side-effect-free**: They do not access external resources, databases, or network services.
- **Without access to environment variables**: Functions cannot read PDP-level configuration.

Because of these properties, functions can be used in **any part** of a SAPL document, including target expressions. The engine can safely evaluate them at any time without concern for ordering, external availability, or subscription lifecycle.

Functions are organized in **function libraries**. Each library has a name consisting of one or more identifiers separated by periods (e.g., `simple.string` or `filter`). The fully qualified name of a function consists of the library name followed by a period and the function name (e.g., `simple.string.append`). [Imports](../2_8_Imports/) can shorten these names.

SAPL ships with a standard set of function libraries. See [Functions](../3_0_Functions/) for the complete reference of built-in function libraries.

### Attribute Finders and Policy Information Points

When a PEP constructs an authorization subscription, it includes information available in the application's current context: the authenticated user (subject), the requested operation (action), the target resource, and contextual data (environment). This represents what the application **knows at that moment**.

However, policies often require information that is not readily available to the PEP:

- A user's department membership (stored in HR systems)
- Security clearance levels (maintained in identity management)
- Current work schedules (managed by scheduling systems)
- Real-time conditions (current time, system status, resource availability)

**Policy Information Points (PIPs)** bridge this knowledge gap by fetching attributes from external sources on demand. In SAPL, PIPs are accessed through **attribute finders** - a dedicated syntax that signals external I/O and potential streaming behavior.

Consider a policy that needs to check a user's role stored in an external user directory:

```sapl
policy "doctors read patient data"
permit
    action == "read" & resource.type == "patient_record";
    subject.username.<user.profile>.function == "doctor";
```

The expression `subject.username.<user.profile>` uses an attribute finder to fetch the user's profile from an external PIP. The result is a JSON object whose `function` attribute is compared to `"doctor"`.

Unlike functions, attribute finders are:

- **Asynchronous**: They may involve network calls, database queries, or other I/O operations.
- **Non-deterministic**: The same attribute may return different values at different times (current time, a user's role after a promotion, a sensor reading).
- **Subscription-based**: They return data streams. The PDP subscribes to them, and the PIP pushes new values whenever the underlying data changes.
- **Not safe for target expressions**: Because they may be slow and involve external dependencies, attribute finders must not be used in target expressions.

Attribute finders can access environment variables configured at the PDP level, unlike functions.

This distinction is why attributes use a dedicated syntax (angle brackets `<...>`) rather than the function call syntax. The angle brackets signal to both the reader and the engine that this expression involves external I/O and may trigger ongoing subscriptions.

### Static vs. Streaming Attributes

The authorization subscription provides a **snapshot** of what the PEP knows when making the request. Once sent, these attributes do not change unless the PEP creates a new subscription. This works well for stable data like usernames or resource identifiers, but many authorization decisions depend on **dynamic conditions** that change over time.

Because the authorization protocol is based on the PDP pushing decisions to the PEP (not the PEP pushing updated attributes), **dynamic attributes must come from PIPs**. This is why time-based policies use `<time.now>` rather than expecting the PEP to include timestamps - the PDP needs to access time continuously, not just at subscription creation.

PIPs enable both:

1. **Bridging knowledge gaps**: Fetching data the PEP does not have
2. **Enabling streaming policies**: Providing dynamic attributes that update over time

### Streaming Attributes and Continuous Authorization

Consider access control based on work shifts:

```sapl
policy "read patient records during business hours"
permit
    resource.type == "patient_record" & action == "read";
    subject.role == "doctor";
    resource.department == subject.department;
    <time.localTimeIsBetween("08:00:00", "18:00:00")>;
```

The `<time.localTimeIsBetween(...)>` attribute streams. When a doctor is granted access at 17:59, the PEP receives PERMIT and maintains the connection. At exactly 18:00:01, the PDP automatically pushes a new DENY decision - without any polling or manual refresh. The PEP can then terminate the session or deny further operations.

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

SAPL includes a library of built-in PIPs for common authorization needs: time and date operations, HTTP access, JWT token handling, and more. See [Attribute Finders](../4_0_AttributeFinders/) for the complete reference of built-in PIPs.

**SAPL's plugin architecture enables domain-specific authorization.** Organizations can implement custom PIPs as plugins to integrate their domain-specific data sources and business logic into authorization policies:

- HR systems and organizational hierarchies
- Scheduling and calendar systems
- Compliance and regulatory engines
- Real-time monitoring and metrics
- Any database, API, or system relevant to authorization decisions

Custom PIPs are implemented as plugins to the PDP, requiring no modifications to SAPL core. Implementation details are covered in [Custom Attribute Finders](../6_6_CustomAttributeFinders/).

### Structuring Policy Conditions

While there is no grammar-level distinction between body statements, it is good practice to put fast, local checks first and slower PIP-based lookups later. This helps the engine skip expensive external calls early when simple conditions already determine the outcome.

**Recommended ordering:**

1. **Fast local checks first**: Resource type, action, simple equality checks on subscription attributes. These evaluate instantly and can short-circuit the rest.
2. **PIP-based lookups later**: Attribute finder expressions may involve network calls or database queries and should only run when the fast checks have already passed.

For details on how the engine optimizes evaluation order across cost strata, see [Evaluation Semantics](../2_10_EvaluationSemantics/).

> **Policy sets** have a dedicated `FOR` clause that acts as a target expression for filtering which policies in the set are evaluated. See [Policy Sets](../2_5_PolicySets/) for details.

### Attribute Finder Parameters and Options

Attribute finders accept **parameters** in parentheses and **options** in square brackets. For the expression syntax, see the [attribute finder step](../2_6_Expressions/#attribute-finder-findername) in Expressions.

#### Parameters

Parameters are additional inputs passed to a PIP inside parentheses. They are positional and can be any SAPL expression, including literals, variables, or function results.

What parameters mean depends on the PIP. Common uses include temporal boundaries, configuration objects, and behavior modifiers:

```sapl
<time.now(5000)>                              // update interval in milliseconds
<time.localTimeIsBetween("09:00", "17:00")>   // start and end times
<http.get(requestSettings)>                   // request configuration object
topic.<mqtt.messages(1)>                      // QoS level
```

PIPs can be overloaded by parameter count. For example, `<time.now>` returns the current time with a default update interval, while `<time.now(5000)>` does the same with a 5-second interval.

#### Options

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

#### Priority Chain

Options follow a three-level priority chain:

1. **Policy-level options** (in square brackets in the policy expression) have the highest priority.
2. **PDP-level defaults** (configured in the PDP settings under `variables.attributeFinderOptions`, see [SAPL Node](../7_1_SAPLNode/)) override built-in defaults.
3. **Built-in defaults** (listed in the table above) apply when no override is specified.

This allows operators to tune stream behavior globally without modifying policies, while individual policies can override when needed.

### The Attribute Broker

The attribute broker mediates between policies and PIPs, managing stream lifecycle and resilience.

#### For Policy Authors

- **Stream sharing:** Identical attribute accesses share one PIP connection, reducing load on external systems. The `fresh` option bypasses this cache when a policy needs an independent stream.
- **The resilience pipeline:** Every attribute lookup is automatically wrapped in a pipeline: timeout, retry with exponential backoff, poll on stream completion, and error-to-undefined conversion. Each option (`initialTimeOutMs`, `retries`, `backoffMs`, `pollIntervalMs`) controls one stage.

#### For PDP Operators

- **Global option defaults:** Configure default attribute finder options under `variables.attributeFinderOptions` in the PDP settings to tune stream behavior across all policies without modifying them. See [SAPL Node](../7_1_SAPLNode/) for configuration details.
- **Grace period:** After the last subscriber disconnects, the broker keeps PIP connections alive for 3 seconds. This prevents unnecessary reconnections when policies are rapidly re-evaluated.
- **Hot-swapping:** PIPs can be loaded and unloaded at runtime. Active streams automatically reconnect to newly loaded PIPs.

#### For PIP Developers

- **Resolution priority:** When the broker receives an attribute request, it resolves the PIP using: exact parameter match, then varargs match, then repository fallback, then error.
- **Automatic resilience wrapping:** PIP authors implement only the data-fetching logic. The broker automatically wraps PIP streams with the timeout/retry/polling pipeline.
- **Collision detection:** If multiple PIPs register the same fully qualified attribute name, the broker detects the collision at load time and logs a warning.
