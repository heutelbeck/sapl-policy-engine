# Streaming Attribute Authorization Engine (SAAE) Bill of Materials (BOM)

A Maven bill of materials POM is a utility POM to make authoring of applications using SAPL/SAAE easier.
By including the POM in the `dependencyManagement`section of a POM, the `version` tag of SAPL dependencies can be omitted in the POM or its children. Using a BOM does not directly introduce any direct or transient dependencies. Only when other SAPL dependencies are used managed by the BOM, these are actually introduced into the dependency tree.

How to use the BOM:

```xml
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>io.sapl</groupId>
				<artifactId>sapl-bom</artifactId>
				<version>2.0.0-SNAPSHOT</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		<dependencies>			
	</dependencyManagement>
```
	
If you are using a SNAPSHOT release, make sure to add the snapshot repository as described here: <https://github.com/heutelbeck/sapl-policy-engine/blob/master/README.md>.
