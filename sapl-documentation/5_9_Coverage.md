---
layout: default
title: Coverage
parent: Testing SAPL Policies
grand_parent: SAPL Reference
nav_order: 309
---

## Coverage

Tests verify that policies behave correctly for specific scenarios, but they do not show which parts of a policy were actually exercised. Coverage analysis closes this gap. It tracks which policy sets, policies, conditions, and branches were evaluated during testing and highlights what remains untested.

The SAPL Maven plugin integrates coverage into the build lifecycle. It collects coverage data during test execution, generates reports, and optionally enforces minimum thresholds. When a threshold is not met, the build fails. This makes the plugin a quality gate for policy code: the same `mvn verify` that runs in development and in CI pipelines ensures that policies meet a defined level of test coverage before they can be merged or deployed.

### Coverage Metrics

| Metric                     | Description                                                          |
|----------------------------|----------------------------------------------------------------------|
| Policy set hit ratio       | Percentage of policy sets that were evaluated during testing         |
| Policy hit ratio           | Percentage of individual policies that were evaluated during testing |
| Policy condition hit ratio | Percentage of condition branches that were exercised (true and false) |
| Branch coverage            | Overall branch coverage across all policy documents                  |

### Maven Plugin Configuration

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

### Configuration Parameters

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

### Plugin Goals

The plugin provides two goals that should be executed together:

| Goal                          | Phase                  | Description                                                  |
|-------------------------------|------------------------|--------------------------------------------------------------|
| `enable-coverage-collection`  | `process-test-classes` | Cleans the coverage output directory before tests run        |
| `report-coverage-information` | `verify`               | Reads coverage data, generates reports, validates thresholds |

### Coverage Output

Coverage data is written to `target/sapl-coverage/coverage.ndjson` during test execution. The report goal reads this data and produces:

- **HTML report** in `target/sapl-coverage/html/` with line-level coverage highlighting per policy file
- **SonarQube report** in `target/sapl-coverage/sonar/sonar-generic-coverage.xml` (when enabled)

### SonarQube Integration

To import SAPL coverage into SonarQube, enable the SonarQube report and configure the import path in your SonarQube project settings:

```xml
<configuration>
    <enableSonarReport>true</enableSonarReport>
</configuration>
```

Add the report path to your SonarQube configuration:

```
sonar.coverageReportPaths=target/sapl-coverage/sonar/sonar-generic-coverage.xml
```

The generated report uses SonarQube's generic test coverage format, which is supported by all SonarQube editions.
