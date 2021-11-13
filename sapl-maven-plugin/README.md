# SAPL Maven Plugin

This module contains the source code of the SAPL Maven Plugin.

For a detailed description of the usage and the available parameters for the SAPL Maven Plugin, consult the [SAPL Docs](https://sapl.io/docs/sapl-reference.html#code-coverage-reports-via-the-sapl-maven-plugin).

## Top-level packages

The at the top-level defined classes ending on *Mojo.java are the entry point of the plugin called by the Maven lifecycle. For further information see [here](https://maven.apache.org/guides/plugin/guide-java-plugin-development.html).

The `ReportCoverageInformationMojo.java` is typically executed by the Maven `verify` goal and collects the coverage hit information, parses the available coverage targets, calculates the coverage ratio for the coverage criteria, and generates the reports.

**helper**:
Contains Helper Utility classes to 
- read coverage hit information via the [sapl-coverage-api](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-coverage-api)
- parse the available SAPL policies and generate the available coverage targets
- calculate the coverage ratios

**model**:
Contains POJO models used in the SAPL Maven Plugin

**report**:
Contains all code necessary to generate the various reports. 
In this package, the `GenericCoverageReporter.java` generates a generic coverage report format. A subpackage is defined for every supported report format, which produces the specific report from the generic coverage report format.