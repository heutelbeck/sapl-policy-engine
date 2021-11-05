# Sonar Generic Coverage Report for SAPL

Sonarqube defines a generic XSD to enable generic coverage reports. [Docs](https://docs.sonarqube.org/latest/analysis/generic-test/).

This reporter generates a file usually located at target/sapl-coverage/sonar/sonar-generic-coverage.xml.

To tell SonarQube to collect this generic coverage report you have to set the parameter 
	
	-Dsonar.coverageReportPaths=target/sapl-coverage/sonar/sonar-generic-coverage.xml


In this file every sapl policy is referenced via it's path in the src/main/resources directory.

By default, the sonar maven plugin only collects files at the paths "pom.xml,src/main/java". This is the default setting for the "sonar.sources" parameter.

To collect the SAPL policies in the src/main/resources directory you have to specifiy some additional parameters at sonar maven plugin execution time.

	-Dsonar.sources=. -Dsonar.inclusions=pom.xml,src/main/java/**,src/main/resources/**

Sadly SonarQube currently does not support generic coverage reports for unknwon languages like SAPL. There is an open [Issue](https://jira.sonarsource.com/browse/SONAR-12015) to use this feature with unknwon languages or to easily integrate new languages only via name and file extension but open to this time.

