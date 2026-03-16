---
layout: default
title: Coverage
parent: Testing SAPL Policies
nav_order: 309
---

## Coverage

Tests verify that policies behave correctly for specific scenarios, but they do not show which parts of a policy were actually exercised. Coverage analysis closes this gap. It tracks which policy sets, policies, conditions, and branches were evaluated during testing and highlights what remains untested.

### Coverage Metrics

| Metric                     | Description                                                           |
|----------------------------|-----------------------------------------------------------------------|
| Policy set hit ratio       | Percentage of policy sets that were evaluated during testing          |
| Policy hit ratio           | Percentage of individual policies that were evaluated during testing  |
| Policy condition hit ratio | Percentage of condition branches that were exercised (true and false) |
| Branch coverage            | Overall branch coverage across all policy documents                   |

### CLI Coverage

The `sapl test` command collects coverage data, generates reports, and optionally enforces minimum thresholds. When a threshold is not met, the command exits with code `3`. This makes `sapl test` a quality gate for policy code in any CI pipeline.

Run tests with coverage thresholds:

```bash
sapl test --dir ./policies --policy-hit-ratio 100 --condition-hit-ratio 70
```

#### Options

| Option                     | Default            | Description                                             |
|----------------------------|--------------------|---------------------------------------------------------|
| `--policy-set-hit-ratio`   | `0`                | Required percentage of policy sets evaluated (0-100)    |
| `--policy-hit-ratio`       | `0`                | Required percentage of policies evaluated (0-100)       |
| `--condition-hit-ratio`    | `0`                | Required percentage of condition branches covered (0-100) |
| `--branch-coverage-ratio`  | `0`                | Required overall branch coverage (0-100)                |
| `--html` / `--no-html`    | `--html`           | Generate an HTML coverage report                        |
| `--sonar` / `--no-sonar`  | `--no-sonar`       | Generate a SonarQube-compatible XML report              |
| `--output`                 | `./sapl-coverage`  | Output directory for coverage data and reports          |

A threshold of `0` disables the check. Any value from `1` to `100` enforces that minimum.

#### Exit Codes

| Code | Meaning                                                   |
|------|-----------------------------------------------------------|
| `0`  | All tests passed and quality gate met (if configured)     |
| `1`  | Error during test execution (I/O, parse errors)           |
| `2`  | One or more tests failed                                  |
| `3`  | Tests passed but coverage is below the required threshold |

#### Reports

Coverage data is written to `<output>/coverage.ndjson` during test execution. Depending on the report options, the command also produces:

- **HTML report** in `<output>/html/` with line-level coverage highlighting per policy file
- **SonarQube report** in `<output>/sonar/sonar-generic-coverage.xml`

#### SonarQube Integration

Generate a SonarQube-compatible report and point SonarQube at the output:

```bash
sapl test --dir ./policies --sonar --output ./sapl-coverage
```

Add the report path to your SonarQube configuration:

```
sonar.coverageReportPaths=sapl-coverage/sonar/sonar-generic-coverage.xml
```

The generated report uses SonarQube's generic test coverage format, which is supported by all SonarQube editions.

### Maven Plugin

For Java projects, the SAPL Maven plugin integrates coverage into the build lifecycle. It collects coverage data during test execution, generates reports, and optionally enforces minimum thresholds. When a threshold is not met, the build fails. This makes the plugin a quality gate alongside `mvn verify`.

```xml
<plugin>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-maven-plugin</artifactId>
    <version>${sapl.version}</version>
    <configuration>
        <policySetHitRatio>100</policySetHitRatio>
        <policyHitRatio>100</policyHitRatio>
        <policyConditionHitRatio>70</policyConditionHitRatio>
        <branchCoverageRatio>0</branchCoverageRatio>
        <enableHtmlReport>true</enableHtmlReport>
        <enableSonarReport>false</enableSonarReport>
        <failOnDisabledTests>true</failOnDisabledTests>
    </configuration>
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
```

#### Configuration Parameters

| Parameter                  | Default | Description                                                |
|----------------------------|---------|------------------------------------------------------------|
| `policySetHitRatio`        | `0`     | Required percentage of policy sets evaluated (0-100)       |
| `policyHitRatio`           | `0`     | Required percentage of policies evaluated (0-100)          |
| `policyConditionHitRatio`  | `0`     | Required percentage of condition branches covered (0-100)  |
| `branchCoverageRatio`      | `0`     | Required overall branch coverage (0-100)                   |
| `enableHtmlReport`         | `true`  | Generate an HTML coverage report                           |
| `enableSonarReport`        | `false` | Generate a SonarQube-compatible XML report                 |
| `failOnDisabledTests`      | `true`  | Fail the build if tests are skipped                        |
| `coverageEnabled`          | `true`  | Enable or disable coverage collection                      |

#### Plugin Goals

The plugin provides two goals that should be executed together:

| Goal                          | Phase                  | Description                                                  |
|-------------------------------|------------------------|--------------------------------------------------------------|
| `enable-coverage-collection`  | `process-test-classes` | Cleans the coverage output directory before tests run        |
| `report-coverage-information` | `verify`               | Reads coverage data, generates reports, validates thresholds |

#### Coverage Output

Coverage data is written to `target/sapl-coverage/coverage.ndjson` during test execution. The report goal reads this data and produces:

- **HTML report** in `target/sapl-coverage/html/` with line-level coverage highlighting per policy file
- **SonarQube report** in `target/sapl-coverage/sonar/sonar-generic-coverage.xml` (when enabled)

#### SonarQube Integration

To import SAPL coverage into SonarQube, enable the SonarQube report and configure the import path:

```xml
<configuration>
    <enableSonarReport>true</enableSonarReport>
</configuration>
```

Add the report path to your SonarQube configuration:

```
sonar.coverageReportPaths=target/sapl-coverage/sonar/sonar-generic-coverage.xml
```
