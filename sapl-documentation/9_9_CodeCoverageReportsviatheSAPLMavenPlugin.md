---
layout: default
title: Code Coverage Reports via the SAPL Maven Plugin
#permalink: /reference/Code-Coverage-Reports-via-the-SAPL-Maven-Plugin/
parent: Testing SAPL policies
grand_parent: SAPL Reference
nav_order: 9
---

## Code Coverage Reports via the SAPL Maven Plugin

For measuring the policy code coverage of SAPL policies, developers can use the SAPL Maven
Plugin to analyze the
coverage and generate reports in various formats.

Currently, three coverage criteria are supported:

* **PolicySet Hit Coverage**: Measures the percentage of PolicySets that were at least
  once applicable to an
  AuthorizationSubscription in the tests.
* **Policy Hit Coverage**: Measures the percentage of Policies that were at least once
  applicable to an
  AuthorizationSubscription in the tests.
* **Condition Hit Coverage**: Measures the percentage of conditions evaluated to true or
  false during the tests. The
  number of conditions times two is compared with the number of positively and negatively
  evaluated conditions.

### Plugin Goals

| **Goal**                         | **Description**                                                                                |
|:---------------------------------|:-----------------------------------------------------------------------------------------------|
| sapl:enable-coverage-collection  | No description                                                                                 |
| sapl:report-coverage-information | Collect coverage information and generate reports. Print path to HTML report in the Maven log. |

### Usage

The SAPL Maven Plugin can be added to the Maven project by adding the following
configuration to the `pom.xml`:

```xml

<plugin>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-maven-plugin</artifactId>
    <configuration>
        <policyHitRatio>100</policyHitRatio>
        <policyConditionHitRatio>50</policyConditionHitRatio>
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

The SAPL Maven Plugin can be invoked by calling the verify phase of the Maven build
lifecyle.

```
mvn verify
```

### Configuration

You can specify the configuration of the SAPL Maven Plugin in the configuration section of
the plugin in the pom.xml.
The plugin can be configured via the following parameters:

* **coverageEnabled**: When set to false, this parameter disables the execution of the
  SAPL Maven Plugin (defaultValue =
  true).
* **policyPath**: Defines the path in the classpath to the folder containing the policies
  under test. Specify the same
  path used in the SaplIntegrationTestFixture or the parent folder of the path to the SAPL
  documents in the
  SaplUnitTestFixture (defaultValue = policies).
* **outputDir**: Set this parameter to the path where generated reports should be
  written (per default, the Maven build
  output directory is used).
* **policySetHitRatio**: A value between 0 - 100 to define the ratio of PolicySets the
  tests should cover. If this ratio
  isn’t fulfilled, the SAPL Maven Plugin is going to stop the Maven lifecycle (
  defaultValue = 0).
* **policyHitRatio**: A value between 0 - 100 to define the ratio of Policies the tests
  should cover. If this ratio
  isn’t fulfilled, the SAPL Maven Plugin is going to stop the Maven lifecycle (
  defaultValue = 0).
* **policyConditionHitRatio**: A value between 0 - 100 to define the ratio of condition
  results the tests should cover.
  If this ratio isn’t fulfilled, the SAPL Maven Plugin is going to stop the Maven
  lifecycle (defaultValue = 0).
* **enableSonarReport**: When set to true, a coverage report with
  the [SonarQube/SonarCloud Generic Coverage Format](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/test-coverage/generic-test-data/)
  is generated. To use this coverage report in the SonarQube/SonarCloud analysis, a
  workaround currently needs to be
  applied, since SonarQube and SonarCloud do not import generic coverage data for
  languages unknown to them. Currently,
  there is also no SonarQube Language plugin for SAPL. The workaround consists of adding
  files with the .sapl file
  extension to a language known to SonarQube/SonarCloud and, at the same time, to ignore
  any issues raised in these
  files.

  First add the Sonar Maven Plugin to your Maven project (see the documentation
  from [SonarQube](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/)
  and [SonarCloud](https://docs.sonarsource.com/sonarcloud/advanced-setup/ci-based-analysis/sonarscanner-for-maven/)
  for
  details).

  To add the coverage report, add the following parameters when you call the Maven sonar
  goal on your project:

    1. By default, the Sonar Maven Plugin only collects files at the paths "
       pom.xml,src/main/java". To collect the SAPL
       policies in the src/main/resources directory you have to specify the following two
       parameters:
          ```
          -Dsonar.sources=. \
          -Dsonar.inclusions=pom.xml,src/main/java/**,src/main/resources/**
          ```
    2. To tell SonarQube or SonarCloud to collect the generic coverage report you have to
       set the following parameter:
         ```
        -Dsonar.coverageReportPaths=target/sapl-coverage/sonar/sonar-generic-coverage.xml
        ```
    3. To add files with the .sapl file extension to a language known to
       SonarQube/SonarCloud you could add it for
       example
       to the yaml language with the following parameter:
        ```
       -Dsonar.yaml.file.suffixes=.yaml,.yml,.sapl
       ```
    4. To ignore any issues raised in the .sapl files, add these last three parameters:
        ```
        -Dsonar.issue.ignore.multicriteria=e1 \
        -Dsonar.issue.ignore.multicriteria.e1.ruleKey=* \
        -Dsonar.issue.ignore.multicriteria.e1.resourceKey=**/*.sapl
        ```
    5. Complete example:
        ```
       mvn clean verify sonar:sonar \
        -Dsonar.sources=. \
        -Dsonar.inclusions=pom.xml,src/main/java/**,src/main/resources/** \
        -Dsonar.coverageReportPaths=target/sapl-coverage/sonar/sonar-generic-coverage.xml \
        -Dsonar.yaml.file.suffixes=.yaml,.yml,.sapl \
        -Dsonar.issue.ignore.multicriteria=e1 \
        -Dsonar.issue.ignore.multicriteria.e1.ruleKey=* \
        -Dsonar.issue.ignore.multicriteria.e1.resourceKey=**/*.sapl
       ```

  These parameters could also be configured in your pom.xml or the global settings.xml.
  See
  the [Sonar Maven Plugin documentation](https://docs.sonarsource.com/sonarcloud/advanced-setup/ci-based-analysis/sonarscanner-for-maven/#configuration)
  for details (defaultValue = false).

* **enableHtmlReport**: When set to true an HTML coverage report is created. This report
  is similar to JaCoCo reports
  showing colorized line coverage and the number of covered branches for conditions in a
  line. The path to the
  index.html on the filesystem is printed in the Maven log. Terminals like Powershell
  allow clicking on these paths and
  opening the report directly in the browser (defaultValue = true).
* **failOnDisabledTests**: When set to true the Maven build will fail if the build has
  been run with -DskipTests or
  -Dmaven.skip.tests=true. When set to false coverage validation will be skipped if tests
  are skipped, but the build
  will not fail (defaultValue = true).
