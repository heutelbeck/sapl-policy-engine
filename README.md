[![Build Status](https://github.com/heutelbeck/sapl-policy-engine/actions/workflows/build_master.yml/badge.svg)](https://github.com/heutelbeck/sapl-policy-engine/actions/workflows/build_master.yml)
[![SonarCloud Status](https://sonarcloud.io/api/project_badges/measure?project=heutelbeck_sapl-policy-engine&metric=alert_status)](https://sonarcloud.io/dashboard?id=heutelbeck_sapl-policy-engine)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=heutelbeck_sapl-policy-engine&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=heutelbeck_sapl-policy-engine)
[![Maven Central](https://img.shields.io/maven-central/v/io.sapl/sapl-lang)](https://img.shields.io/maven-central/v/io.sapl/sapl-lang)
[![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fs01.oss.sonatype.org%2Fcontent%2Frepositories%2Fsnapshots%2Fio%2Fsapl%2Fsapl-policy-engine%2Fmaven-metadata.xml)](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fs01.oss.sonatype.org%2Fcontent%2Frepositories%2Fsnapshots%2Fio%2Fsapl%2Fsapl-policy-engine%2Fmaven-metadata.xml)
[![Discord](https://img.shields.io/discord/988472137306222654)](https://img.shields.io/discord/988472137306222654)

# The Streaming Attribute Policy Language (SAPL) and the Streaming Attribute Authorization Engine (SAAE)

## Maven Dependencies

### Add the Maven Central snapshot repository to your `pom.xml`, if you want to use SNAPSHOT versions of the engine:

```xml
  <repositories>
    <repository>
      <id>ossrh</id>
      <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
  </repositories>
```

### SAPL requires Java 17 or later

```xml
  <properties>
    <java.version>17</java.version>
    <maven.compiler.source>${java.version}</maven.compiler.source>
    <maven.compiler.target>${java.version}</maven.compiler.target>
  </properties>
```

### Add a SAPL dependency to your application. When using Maven you can add the following dependencies to your project's `pom.xml`:

```xml
  <dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-pdp-embedded</artifactId>
    <version>3.0.0-SNAPSHOT</version>
  </dependency>
```

### If you plan to use more SAPL dependencies, a useful bill of materials POM is offered, centralizing the dependency management for SAPL artifacts:

```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.sapl</groupId>
        <artifactId>sapl-bom</artifactId>
        <version>3.0.0-SNAPSHOT</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
```
