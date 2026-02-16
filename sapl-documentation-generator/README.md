# SAPL Documentation Generator

A Spring Boot application that automatically generates Jekyll-compatible markdown documentation from annotated SAPL function libraries and policy information points (PIPs).

## Overview

The documentation generator uses Java reflection to extract documentation metadata from SAPL extension classes annotated with `@FunctionLibrary` and `@PolicyInformationPoint`. It produces markdown files with Jekyll front matter, suitable for publishing on the sapl.io documentation website.

## How It Works

1. The application starts as a Spring Boot application
2. On startup, it scans configured library and PIP classes
3. The `LibraryDocumentationExtractor` (from `sapl-pdp`) extracts documentation from annotations
4. Markdown files are generated with Jekyll front matter for website integration
5. The application exits after generating all documentation

### Extracted Information

For each library, the generator extracts:

- Library name and description from `@FunctionLibrary` or `@PolicyInformationPoint`
- Library-level documentation from the `libraryDocumentation` or `pipDocumentation` attribute
- Per-function/attribute documentation from `@Function`, `@Attribute`, or `@EnvironmentAttribute`
- Parameter information including types and varargs
- JSON schemas when provided

## Configuration

### Application Properties

```yaml
application:
  version: '@project.version@'  # Injected from Maven

sapl:
  documentation:
    target: target/doc  # Output directory for generated markdown files
```

### Adding Libraries

To include additional libraries for documentation generation, modify `DocumentationGenerator.java`:

```java
// Function Libraries
val libraries = new ArrayList<Class<?>>(DefaultLibraries.STATIC_LIBRARIES);
libraries.add(GeographicFunctionLibrary.class);
libraries.add(MqttFunctionLibrary.class);
// Add your custom library here:
libraries.add(YourCustomFunctionLibrary.class);

// Policy Information Points
val pips = new ArrayList<Class<?>>();
pips.add(MqttPolicyInformationPoint.class);
pips.add(TraccarPolicyInformationPoint.class);
// Add your custom PIP here:
pips.add(YourCustomPolicyInformationPoint.class);
```

## Usage

### Local Development

Build and run the documentation generator:

```bash
# Build required modules
mvn clean install -pl sapl-documentation-generator -am -DskipTests

# Run the generator
cd sapl-documentation-generator
mvn spring-boot:run
```

Generated files appear in `target/doc/` with the naming convention:
- `lib_<name>.md` for function libraries
- `pip_<name>.md` for policy information points

### CI/CD Integration

The generator runs automatically in the `build_documentation.yml` GitHub Actions workflow:

1. Builds the module and its dependencies
2. Executes the generator via `mvn spring-boot:run`
3. Combines output with static documentation from `sapl-documentation/`
4. Pushes to the `sapl-pages` repository for website deployment

## Output Format

Generated markdown files include Jekyll front matter:

```markdown
---
layout: default
title: filter
parent: Functions
grand_parent: SAPL Reference
nav_order: 101
---

# filter

Library description here.

Detailed library documentation here.

---

## functionName

Function documentation here.

---
```

## Dependencies

The generator depends on:

- `sapl-pdp` - Contains `LibraryDocumentationExtractor` and core libraries
- `geo-functions` - Geographic function library
- `geo-traccar` - Traccar GPS policy information point
- `mqtt-functions` - MQTT function library
- `mqtt-pip` - MQTT policy information point

## Writing Documentation for Your Libraries

To make your custom libraries compatible with the documentation generator, use the standard SAPL annotations:

### Function Library Example

```java
@FunctionLibrary(
    name = "mylib",
    description = "Short description for IDE tooltips",
    libraryDocumentation = """
        Detailed markdown documentation about the library.
        Can include examples, use cases, and extended explanations.
        """
)
public class MyFunctionLibrary {

    @Function(
        name = "process",
        docs = """
            Processes the input value according to access control rules.

            **Parameters:**
            - `input`: The value to process

            **Returns:** The processed result

            **Example:**
            ```
            mylib.process("sensitive-data")
            ```
            """
    )
    public static Value process(@Text TextValue input) {
        // implementation
    }
}
```

### Policy Information Point Example

```java
@PolicyInformationPoint(
    name = "mydata",
    description = "Provides access to external data sources",
    pipDocumentation = """
        This PIP connects to external systems to retrieve
        authorization-relevant data at policy evaluation time.
        """
)
public class MyPolicyInformationPoint {

    @Attribute(
        name = "userRole",
        docs = """
            Retrieves the role of a user from the identity provider.

            **Entity:** User identifier
            **Returns:** Flux of role values that updates on changes
            """
    )
    public Flux<Value> userRole(Value userId, AttributeAccessContext ctx) {
        // implementation
    }

    @EnvironmentAttribute(
        name = "currentTime",
        docs = "Returns the current server time as an ISO-8601 string."
    )
    public Flux<Value> currentTime() {
        // implementation
    }
}
```

## Architecture

```
sapl-documentation-generator/
├── src/main/java/io/sapl/documentation/
│   ├── DocumentationGeneratorApplication.java  # Spring Boot entry point
│   └── DocumentationGenerator.java             # Core generation logic
├── src/main/resources/
│   └── application.yml                         # Configuration
└── pom.xml                                     # Dependencies
```

The actual extraction logic resides in `sapl-pdp`:
- `io.sapl.documentation.LibraryDocumentationExtractor` - Reflection-based extraction
- `io.sapl.api.documentation.*` - Documentation DTOs and records

## License

Copyright 2017-2025 Dominic Heutelbeck

Licensed under the Apache License, Version 2.0.
