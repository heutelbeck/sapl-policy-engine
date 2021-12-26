# Streaming Attribute Authorization Engine (SAAE) Bill of Materials (BOM)

A Maven bill of materials POM is a utility POM that makes authoring of applications using SAPL easier.
Including the POM in the `dependencyManagement` section of a POM, the `version` tag of SAPL dependencies can be omitted. Using a BOM does not directly introduce any direct or transient dependencies. 

How to use the BOM:

```xml
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>io.sapl</groupId>
				<artifactId>sapl-bom</artifactId>
				<version>2.0.1-SNAPSHOT</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>			
	</dependencyManagement>
```

If you are using a SNAPSHOT release, make sure to add the snapshot repository as described here: <https://github.com/heutelbeck/sapl-policy-engine/blob/master/README.md>.
