# SAPL Bill of Materials (BOM)

A Maven bill of materials POM makes authoring applications using SAPL easier.
When the BOM is imported in `dependencyManagement`, SAPL dependency versions can be omitted from individual dependency declarations.
Using a BOM does not directly introduce runtime dependencies.
All modules can also be used without the BOM, but then each SAPL dependency must declare a matching version explicitly.

## Stable Release

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-bom</artifactId>
            <version>4.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Development Snapshot

Use a snapshot version only when you intentionally want unreleased development builds.
Only add the snapshot repository when the SAPL version ends in `-SNAPSHOT`.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-bom</artifactId>
            <version>4.1.1-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Snapshot artifacts are resolved from the Central Portal snapshot repository:

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
