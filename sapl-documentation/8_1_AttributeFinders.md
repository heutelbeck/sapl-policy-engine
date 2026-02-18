---
layout: default
title: Attribute Finders
##permalink: /reference/Attribute-Finders/
has_children: true
parent: SAPL Reference
nav_order: 300
has_toc: false
---

## Attribute Finders

> For a conceptual introduction to attribute finders and streaming attributes, see [Accessing Attributes](../1_5_AccessingAttributes/).

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

For details on how the engine applies these options through the resilience pipeline (timeout, retry, polling, stream sharing), see [Attribute Broker](../8_3_AttributeBroker/).