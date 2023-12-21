# SAPL Maven Plugin

This module contains the source code of the SAPL Maven Plugin.

This plugin allows you to measure the code coverage when testing your SAPL policies. It analyzes the coverage using different coverage criteria and generates reports in HTML format. It also allows for integrating coverage information in your SonarQube/SonarCloud analysis.

For a detailed description of the usage and the available parameters for the SAPL Maven Plugin, consult the [SAPL Docs](https://sapl.io/docs/latest/sapl-reference.html#code-coverage-reports-via-the-sapl-maven-plugin).

## Getting Started

To integrate the SAPL Maven Plugin in your SAPL Maven project, add the following configuration to your `pom.xml`:

````
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
````

The SAPL Maven Plugin can be invoked by calling the `verify` phase of the Maven build lifecyle.

````
mvn verify
````

For further configuration options of the SAPL Maven Plugin, consult the [SAPL documentation](https://sapl.io/docs/latest/sapl-reference.html#code-coverage-reports-via-the-sapl-maven-plugin).