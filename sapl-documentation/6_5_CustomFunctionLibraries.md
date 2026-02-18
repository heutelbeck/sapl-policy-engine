---
layout: default
title: Custom Function Libraries
parent: Integration
grand_parent: SAPL Reference
nav_order: 605
---

## Custom Function Libraries

The standard functions can be extended by custom function libraries. SAPL functions are pure data transformations. They must not perform IO operations or access external resources.

### Declaring a Function Library

A class annotated with `@FunctionLibrary` is recognized as a function library:

| Attribute              | Description                                              | Default          |
|------------------------|----------------------------------------------------------|------------------|
| `name`                 | Library name as used in SAPL policies (e.g., `"time"`)   | Java class name  |
| `description`          | Short description for documentation                      | `""`             |
| `libraryDocumentation` | Detailed documentation (supports Markdown)               | `""`             |

```java
@UtilityClass
@FunctionLibrary(name = "sample.functions", description = "A sample function library")
public class SampleFunctionLibrary {
    ...
}
```

### Declaring Functions

Methods annotated with `@Function` are exposed as SAPL functions:

| Attribute      | Description                                           | Default          |
|----------------|-------------------------------------------------------|------------------|
| `name`         | Function name in SAPL (overrides Java method name)    | Method name      |
| `docs`         | Function documentation                                | `""`             |
| `schema`       | Inline JSON schema for the return value               | `""`             |
| `pathToSchema` | Classpath path to a JSON schema file                  | `""`             |

Function methods must be `static` and return `Value`. Parameters use `Value` subtypes directly for type safety:

| SAPL Type | Java Parameter Type |
|-----------|---------------------|
| String    | `TextValue`         |
| Number    | `NumberValue`       |
| Boolean   | `BooleanValue`      |
| Object    | `ObjectValue`       |
| Array     | `ArrayValue`        |
| Any       | `Value`             |

The PDP validates parameter types before calling the function. If a policy passes a value of the wrong type, the function is not invoked and the expression evaluates to an error.

**Single parameter:**

```java
@Function(docs = "Converts the string to lower case.")
public static Value toLowerCase(TextValue str) {
    return Value.of(str.value().toLowerCase());
}
```

**Multiple parameters with mixed types:**

```java
@Function(docs = "Adds a number of days to a UTC timestamp.")
public static Value plusDays(TextValue startTime, NumberValue days) {
    try {
        var instant = Instant.parse(startTime.value());
        return Value.of(instant.plus(days.value().longValue(), ChronoUnit.DAYS).toString());
    } catch (Exception e) {
        return Value.error("Invalid temporal input.", e);
    }
}
```

**No parameters:**

```java
@Function(docs = "Returns a random float between 0.0 and 1.0.")
public static Value randomFloat() {
    return Value.of(SECURE_RANDOM.nextDouble());
}
```

**Variable arguments:**

```java
@Function(docs = "Concatenates all strings.")
public static Value concat(TextValue... strings) {
    var result = new StringBuilder();
    for (var str : strings) {
        result.append(str.value());
    }
    return Value.of(result.toString());
}
```

**Polymorphic input using generic `Value`:**

```java
@Function(docs = "Returns the length of a string, array, or object.")
public static Value length(Value value) {
    return switch (value) {
        case TextValue text     -> Value.of(text.value().length());
        case ArrayValue array   -> Value.of(array.size());
        case ObjectValue object -> Value.of(object.size());
        default                 -> Value.error("Argument must be a string, array, or object.");
    };
}
```

**Custom function name:**

```java
@Function(name = "toString", docs = "Converts any value to its string representation.")
public static Value asString(Value value) {
    // Exposed as "toString" in SAPL policies, but the Java method is named
    // "asString" to avoid conflicts with Object.toString().
    ...
}
```

### Error Handling

Functions must never throw exceptions. When an operation cannot succeed, return an error value using `Value.error()`:

```java
@Function(docs = "Parses a UTC timestamp.")
public static Value parseUTC(TextValue input) {
    try {
        var instant = Instant.parse(input.value());
        return Value.of(instant.toString());
    } catch (DateTimeParseException e) {
        return Value.error("Not a valid UTC timestamp: " + input.value(), e);
    }
}
```

An error value propagates through the policy evaluation and causes the enclosing condition to evaluate to `INDETERMINATE`.

### Registering Custom Libraries

Custom function libraries are registered with the PDP through the builder API:

```java
// Static library (utility class with static methods)
var pdp = PolicyDecisionPointBuilder.builder()
    .withDefaults()
    .withFunctionLibrary(SampleFunctionLibrary.class)
    .build();

// Instantiated library (when the library needs constructor dependencies)
var pdp = PolicyDecisionPointBuilder.builder()
    .withDefaults()
    .withFunctionLibraryInstance(new SampleFunctionLibrary(dependency))
    .build();
```

In a Spring Boot application, any bean annotated with `@FunctionLibrary` is automatically discovered and registered with the PDP.

{: .note }
> Add the `-parameters` flag to the Java compiler to ensure that automatically generated documentation includes parameter names from the source code.
