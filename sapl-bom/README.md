# The Streaming Attribute Policy Language and Engine Bill of Materials (BOM)

A Maven bill of materials POM is a utility POM that makes authoring of applications using SAPL easier.
Including the POM in the `dependencyManagement` section of a POM, the `version` tag of SAPL dependencies can be omitted. Using a BOM does not directly introduce any direct or transient dependencies. All modules can also be used without the BOM in place. However, you will need to specify the matching version for each SAPL dependency individually.


## Latest Release 

```xml
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>io.sapl</groupId>
				<artifactId>sapl-bom</artifactId>
				<version>3.0.0</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>			
	</dependencyManagement>
```

## Current Snapshot:

```XML
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>io.sapl</groupId>
				<artifactId>sapl-bom</artifactId>
				<version>4.0.0-SNAPSHOT</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>			
	</dependencyManagement>
```

Snapshots only become accessible to your build by adding a reference to the respective snapshot repository:

```xml
<repositories>
		<repository>
			<name>Central Portal Snapshots</name>
			<id>central-portal-snapshots</id>
			<url>https://central.sonatype.com/repository/maven-snapshots/</url>
			<releases>
				<enabled>false</enabled>
			</releases>
			<snapshots>
				<enabled>true</enabled>
			</snapshots>
		</repository>
</repositories>
```
