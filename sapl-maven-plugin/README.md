# SAPL Maven Plugin

This modules contains the source code of the SAPL Maven Plugin.

For a detailed description of the usage and the available parameters for the SAPL Maven Plugin consult the [SAPL Docs](https://sapl.io/docs/sapl-reference.html#code-coverage-reports-via-the-sapl-maven-plugin).

## Top level packages

The at the top level defined classes ending on *Mojo.java are the entrypoint of the plugin called by the Maven lifecycle. For further information see [here](https://maven.apache.org/guides/plugin/guide-java-plugin-development.html).

The `ReportCoverageInformationMojo.java` is typically executed by the Maven `verify` goal and collects the coverage hit information, parses the available coverage targets, calculates the coverage ration for the coverage criterias and generates the reports.

**helper**:
Contains Helper Utility classes to 
- read coverage hit information via the [sapl-coverage-api](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-coverage-api)
- parse the available SAPL policies and generate the available coverage targets
- calculate the coverage ratios

**model**:
Contains POJO models used in the SAPL Maven Plugin

**report**:
Contains all code neccessary to generate the various reports. 
In this package the `GenericCoverageReporter.java` generates a generic coverage report format. For every supported report format there is a subpackage defined which generates the specific report from the generic coverage report format.