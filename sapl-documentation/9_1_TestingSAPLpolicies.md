---
layout: default
title: Testing SAPL Policies
##permalink: /reference/Testing-SAPL-policies/
has_children: true
parent: SAPL Reference
nav_order: 9
has_toc: false
---

## Testing SAPL Policies

Policies are code. They encode authorization logic that determines who can access what under which conditions. Like any code, policies can have bugs, miss edge cases, and break when requirements change. SAPL provides a dedicated test DSL for testing policies directly, without writing Java code.

A complete test looks like this:

```sapltest
requirement "patient record access" {
    scenario "doctors can read patient records"
        given
            - document "patient-access"
        when "Dr. Smith" attempts "read" on "patient_record"
        expect permit;

    scenario "nurses cannot delete patient records"
        given
            - document "patient-access"
        when "Nurse Jones" attempts "delete" on "patient_record"
        expect deny;
}
```

Test files use the `.sapltest` extension and are placed alongside your policies. The test framework discovers and executes them automatically.

### Approach

The test DSL follows a BDD-inspired (Behavior-Driven Development) structure similar to frameworks like Cucumber or Spock. Each test is a scenario that describes preconditions, an action, and the expected outcome.

Tests are organized into **requirements** that group related **scenarios**. Each scenario follows a **Given-When-Expect** structure:

- **given** sets up the test: which policy to load, which functions and attributes to mock.
- **when** defines the authorization subscription: who is attempting what action on which resource.
- **expect** declares the expected decision.

The DSL uses `expect` rather than `then` because policy evaluation is a pure computation. There are no side effects to observe. The PDP receives a subscription and produces a decision. `expect` expresses this declarative relationship directly.

### Runtime

Tests are written entirely in the SAPL test DSL. No Java knowledge is required to write or maintain tests. The project setup below provides a lightweight Java runtime to discover and execute them. You need JDK 21 or later and Maven installed on your system.

Future releases of the SAPL policy administration tools will include a built-in test runner with a graphical interface, making even this minimal project setup unnecessary.

### Project Setup

Add the test dependency and coverage plugin to your `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-bom</artifactId>
            <version>${sapl.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.sapl</groupId>
        <artifactId>sapl-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-maven-plugin</artifactId>
            <version>${sapl.version}</version>
            <executions>
                <execution>
                    <id>coverage</id>
                    <goals>
                        <goal>enable-coverage-collection</goal>
                        <goal>report-coverage-information</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Project Layout

Policies live in `src/main/resources/policies/`. Tests live in `src/test/resources/`. A single JUnit adapter class in `src/test/java/` connects the test framework to JUnit:

```
src/
  main/
    resources/
      policies/
        patient-access.sapl
  test/
    java/
      com/example/
        SaplTests.java
    resources/
      patient-access-tests.sapltest
```

The adapter class:

```java
public class SaplTests extends JUnitTestAdapter {
}
```

This empty class discovers all `.sapltest` files in `src/test/resources/` and runs them as JUnit 5 dynamic tests. Run your tests with:

```
mvn verify
```

If your policies use custom function libraries or PIPs, register them in the adapter:

```java
public class SaplTests extends JUnitTestAdapter {

    @Override
    protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
        return Map.of(
            ImportType.STATIC_FUNCTION_LIBRARY,
                Map.of("temporal", TemporalFunctionLibrary.class),
            ImportType.PIP,
                Map.of("user", new UserPIP()));
    }
}
```
