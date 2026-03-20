---
layout: default
title: Testing SAPL Policies
has_children: true
nav_order: 300
has_toc: false
---

## Testing SAPL Policies

Policies are code. They encode authorization logic that determines who can access what under which conditions. Like any code, policies can have bugs, miss edge cases, and break when requirements change. SAPL provides a dedicated test DSL and a built-in test runner for testing policies directly.

A complete test looks like this:

```sapltest
requirement "patient record access" {
    given
        - document "patient-access"

    scenario "doctors can read patient records"
        when "Dr. Smith" attempts "read" on "patient_record"
        expect permit;

    scenario "nurses cannot delete patient records"
        when "Nurse Jones" attempts "delete" on "patient_record"
        expect not-applicable;
}
```

> **Unit tests vs. CLI:** A unit test evaluates a single policy document in isolation. When no rule in that document matches, the result is `NOT_APPLICABLE`. The CLI commands (`decide-once`, `check`) wrap the evaluation in a *combining algorithm* that maps `NOT_APPLICABLE` to `DENY` by default. To test the combined behavior, use integration tests (see [Unit Tests and Integration Tests](../5_8_UnitAndIntegrationTests/)).

Test files use the `.sapltest` extension and are placed alongside your policies.

### Approach

The test DSL follows a BDD-inspired (Behavior-Driven Development) structure similar to frameworks like Cucumber or Spock. Each test is a scenario that describes preconditions, an action, and the expected outcome.

Tests are organized into **requirements** that group related **scenarios**. Each scenario follows a **Given-When-Expect** structure:

- **given** sets up the test: which policy to load, which functions and attributes to mock.
- **when** defines the authorization subscription: who is attempting what action on which resource.
- **expect** declares the expected decision.

The DSL uses `expect` rather than `then` because policy evaluation is a pure computation. There are no side effects to observe. The PDP receives a subscription and produces a decision. `expect` expresses this declarative relationship directly.

### Prerequisites

The `sapl` CLI is required. Download the binary for your platform from the [releases page](https://github.com/heutelbeck/sapl-policy-engine/releases) and verify the installation with `sapl --version`. See [SAPL Node Getting Started](../7_1_GettingStarted/#installing-with-deb-or-rpm) for package installation on Linux.

### Running Tests

The `sapl test` command discovers `.sapl` and `.sapltest` files, runs all scenarios, and generates coverage reports. Place your policies and tests in the same directory:

```
policies/
  patient-access.sapl
  patient-access-tests.sapltest
```

Run the tests:

```bash
sapl test --dir ./policies
```

By default, `sapl test` looks in the current directory. If your policies and tests live in separate directories, use `--testdir`:

```bash
sapl test --dir ./policies --testdir ./tests
```

The test runner prints results per requirement and scenario, with pass/fail status and timing:

```
  patient-access-tests.sapltest
    patient record access
      PASS  doctors can read patient records          12ms
      PASS  nurses cannot delete patient records       8ms

Tests:  2 passed, 2 total
Time:   20ms
```

Exit codes encode the result: `0` for all tests passed, `2` for failures, `3` for quality gate not met (see [Coverage](../5_9_Coverage/)).

### CI/CD Integration

Use `sapl test` in CI pipelines to verify that policy changes do not break expected decisions. The command returns non-zero exit codes on failure, making it a drop-in quality gate.

#### GitHub Actions

The [`setup-sapl`](https://github.com/heutelbeck/setup-sapl) action installs the SAPL CLI on any GitHub Actions runner. It downloads the correct binary for the runner's platform and adds it to the PATH.

Run policy tests:

```yaml
steps:
  - uses: actions/checkout@v4
  - uses: heutelbeck/setup-sapl@v1
  - run: sapl test --dir ./policies
```

Enforce coverage quality gates:

```yaml
steps:
  - uses: actions/checkout@v4
  - uses: heutelbeck/setup-sapl@v1
  - run: sapl test --dir ./policies --policy-hit-ratio 100 --condition-hit-ratio 80
```

Generate a SonarQube coverage report:

```yaml
steps:
  - uses: actions/checkout@v4
  - uses: heutelbeck/setup-sapl@v1
  - run: sapl test --dir ./policies --sonar --output sapl-coverage
  - uses: SonarSource/sonarqube-scan-action@v5
    with:
      args: -Dsonar.coverageReportPaths=sapl-coverage/sonar/sonar-generic-coverage.xml
```

The action supports `ubuntu-latest` (x86_64 and ARM64) and `windows-latest`. See the [setup-sapl README](https://github.com/heutelbeck/setup-sapl) for version pinning and all options.

#### Other CI Systems

On any CI system, download the binary from the [releases page](https://github.com/heutelbeck/sapl-policy-engine/releases) and run:

```bash
sapl test --dir ./policies --policy-hit-ratio 100
```

This fails the build if any policy is not exercised by at least one test. See [Coverage](../5_9_Coverage/) for all available thresholds and report options.

### Java Project Integration

For projects that already use Maven, SAPL tests can run as part of the Maven build lifecycle via JUnit 5. This is useful when your policies live inside a Java project and you want test results in the same `mvn verify` run alongside your application tests.

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
