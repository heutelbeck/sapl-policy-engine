# SAPL Maven Plugin

This module contains the source code of the SAPL Maven Plugin.

For a detailed description of the usage and the available parameters for the SAPL Maven Plugin, consult the [SAPL Docs](https://sapl.io/docs/2.0.1/sapl-reference.html#code-coverage-reports-via-the-sapl-maven-plugin).

## Top-level packages

The at the top-level defined classes ending on *Mojo.java are the entry point of the plugin called by the Maven lifecycle. For further information see [here](https://maven.apache.org/guides/plugin/guide-java-plugin-development.html).

The `ReportCoverageInformationMojo.java` is typically executed by the Maven `verify` goal and collects the coverage hit information, parses the available coverage targets, calculates the coverage ratio for the coverage criteria, and generates the reports.

**helper**:
Contains Helper Utility classes to 
- read coverage hit information via the [sapl-coverage-api](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-coverage-api)
- parse the available SAPL policies and generate the available coverage targets
- calculate the coverage ratios

**model**:
Contains POJO models used in the SAPL Maven Plugin.

**report**:
Contains all code necessary to generate the various reports. 
In this package, the `GenericCoverageReporter.java` generates a generic coverage report format. A subpackage is defined for every supported report format, which produces the specific report from the generic coverage report format.

## Report package
### sonar: Sonar Generic Coverage Report for SAPL

SonarQube and SonarCloud define a generic XSD to enable [generic coverage reports](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/test-coverage/generic-test-data/).

This reporter generates a file usually located at target/sapl-coverage/sonar/sonar-generic-coverage.xml.

To tell SonarQube or SonarCloud to collect this generic coverage report you have to set the parameter 
	
	-Dsonar.coverageReportPaths=target/sapl-coverage/sonar/sonar-generic-coverage.xml


In this file every SAPL policy is referenced via its path in the src/main/resources directory.

By default, the Sonar Maven Plugin only collects files at the paths "pom.xml,src/main/java". This is the default setting for the "sonar.sources" parameter.

To collect the SAPL policies in the src/main/resources directory you have to specify some additional parameters at Sonar Maven Plugin execution time.

	-Dsonar.sources=. -Dsonar.inclusions=pom.xml,src/main/java/**,src/main/resources/**

SonarQube and SonarCloud currently do not support generic coverage reports for unknown languages like SAPL. To use the report in your SonarQube/SonarCloud analysis, a workaround is needed. See the [SAPL documentation](https://sapl.io/docs/2.0.1/sapl-reference.html#code-coverage-reports-via-the-sapl-maven-plugin) for details.

### sonar.model: Generate Models from Sonar Generic Coverage XSD Schema

1. Get schema from [here](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/test-coverage/generic-test-data/).

2. Modify sonar-generic-coverage.xsd to bound schema:
```
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <xs:schema version="1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
```

3. Generate via `xjc -d sonar -p io.sapl.test.mavenplugin.model.sonar sonar-generic-coverage.xsd`
