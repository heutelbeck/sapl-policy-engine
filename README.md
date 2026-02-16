<!-- PROJECT LOGO -->
<br />
<div align="center">
  <a href="https://github.com/heutelbeck/sapl-policy-engine">
    <img src="https://sapl.io/assets/favicon.png" alt="Logo" width="100em" >
  </a>

<h3 align="center">SAPL</h3>

  <p align="center">
    The Streaming Attribute Policy Language and Engine
    <br />
    <a href="https://sapl.io/"><strong>Explore our website »</strong></a>
    <br />
    <br />
    <a href="https://playground.sapl.io/">Playground</a>
    ·
    <a href="https://github.com/heutelbeck/sapl-demos">Demos</a>
    ·
    <a href="https://github.com/heutelbeck/sapl-policy-engine/issues">Report an issue</a>
    ·
    <a href="https://github.com/heutelbeck/sapl-policy-engine/issues">Discord</a>
  </p>
</div>

<!-- PROJECT SHIELDS -->
[![Build Status][build-status-shield]][build-status-url]
[![SonarCloud Status][sonarcloud-status-shield]][sonarcloud-status-url]
[![Security Rating][security-rating-shield]][security-rating-url]
[![Maven Central][maven-central-shield]][maven-central-url]
[![Maven Snapshot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fio%2Fsapl%2Fsapl-lang%2Fmaven-metadata.xml&query=%2Fmetadata%2Fversioning%2Flatest&label=snapshot)](https://central.sonatype.com/artifact/io.sapl/sapl-lang)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/heutelbeck/sapl-policy-engine/badge)](https://securityscorecards.dev/viewer/?uri=github.com/heutelbeck/sapl-policy-engine)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/8298/badge?cache-control=no-cache)](https://www.bestpractices.dev/projects/8298)

<!-- ABOUT THE PROJECT -->
## About The Project

The reactive open-source engine for adding Attribute-Based Access Control (ABAC) to your Java applications, supporting attribute streams for efficient interactive real-time access control.

SAPL is a powerful policy language and engine for implementing ABAC. It comes with development tools for testing, authorization servers, and authoring tools. Framework integrations are available for Spring, Axon, and Vaadin to provide flexible policy enforcement points (PEPs) in your application.

For an explanation, overview, and documentation about the SAPL project look up our [website][website-url].

## Compatibility

| SAPL  | Java    | Spring Boot |
|-------|---------|-------------|
| 4.0.x | 21 - 25 | 4.0.x       |
| 3.0.x | 17+     | 3.x         |

<!-- GETTING STARTED -->
## Getting Started

To get started with integrating SAPL into your Java application, add the following reference to your build tool project definition.

**We recommend using the latest release for stability or the SNAPSHOT version if you want to be up-to-date with the latest new features.**

**Maven**

```xml
<dependency>
  <groupId>io.sapl</groupId>
  <artifactId>sapl-pdp</artifactId>
  <version>4.0.0-SNAPSHOT</version>
</dependency>
```

**Gradle**

```gradle
dependencies {
  implementation 'io.sapl:sapl-pdp:4.0.0-SNAPHOT'
}
```

This enables you to use the interface `PolicyDecisionPoint` to decide about requests.

Want to integrate and understand the full scale of capabilities of SAPL? Visit our [website](https://sapl.io).

Want to see integration examples? View our dedicated [demos](https://github.com/heutelbeck/sapl-demos).

Feeling experimental? Use our snapshots for the newest development state!

## IDE-Support

<!-- Eclipse -->
## SAPL Eclipse Plug-in


Get code editing support in Eclipse by installing the SAPL Plug-in: [![Install SAPL Plugin](https://img.shields.io/badge/Eclipse%20Marketplace-Install-blueviolet?logo=eclipse)](https://marketplace.eclipse.org/marketplace-client-intro?mpc_install=5795798 "Drag to your running Eclipse workspace. Requires Eclipse Marketplace Client")

### IntelliJ IDEA Plug-in

Get code editing support by installing the [SAPL Plug-in for IntelliJ IDEA](https://github.com/heutelbeck/sapl-intellij-plugin).

### Other IDEs

SAPL provides a language server for the integration into other IDEs which support the language server protocol.
For details see [sapl-language-server](sapl-language-server/README.md).

<!-- DEMOS -->
## Want to integrate SAPL?

SAPL supports different integration scenarios, which are partially described on our [website][website-url].

If you want to see examples, view our [demo repository][demos-url] to give you a gist about how you could integrate SAPL.

**Need a [SBOM][sbom-definition-url]? View [here][sbom-extraction-url].**

<!-- CONTRIBUTING -->
## Want to contribute to SAPL?

Any contributions you make are **greatly appreciated**.

See our [Contribution document](CONTRIBUTING.md) for more detailed information on how to contribute.

<!-- SECURITY -->
## Found a vulnerability?

The project is committed to identifying and eliminating any potential weaknesses in its security.

See our [Security document](SECURITY.md) for more detailed information on how to report vulnerabilities.

<!-- SNAPSHOTS REFERENCE -->
## Snapshots

This project provides snapshots of the newest development state to enable testing and integration.

**Be careful when using snapshots as they may be broken!**

To add snapshot references to your project add the following references to your build tool project definition.

**Maven**

By default, Maven only retrieves dependencies from the central releases repository. To get access to the snapshot 
builds, the matching snapshots repository must be added to the projects ```pom.xml```.   

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

After doing so, the projects now has access to the SAPL snapshot builds which can be added as follows:

```
<dependencies>
  <dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-{package}</artifactId>
    <version>4.0.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

**Gradle**

By default, Gradle only retrieves dependencies from the central releases repository. To get access to the snapshot
builds, the matching snapshots repository must be added to the project configuration.

```gradle
repositories {
  maven {
    url = uri("https://central.sonatype.com/repository/maven-snapshots")
  }
}

dependencies {
  implementation 'io.sapl:{package}4.0.0-SNAPSHOT'
}
```

**You need to replace `{package}` with the designated project!**


<!-- CODE OF CONDUCT -->
## Code of Conduct

This project has adopted a [Code of Conduct](CODE_OF_CONDUCT.md), and it will be enforced in any communication.

<!-- LICENSE -->
## License

Distributed under the Apache 2.0 License. See [LICENSE.md](./LICENSE.md) for more information.

<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[build-status-shield]: https://github.com/heutelbeck/sapl-policy-engine/actions/workflows/build.yml/badge.svg?branch=master
[build-status-url]: https://github.com/heutelbeck/sapl-policy-engine/actions/workflows/build.yml?branch=master
[sonarcloud-status-shield]: https://sonarcloud.io/api/project_badges/measure?project=heutelbeck_sapl-policy-engine&metric=alert_status
[sonarcloud-status-url]: https://sonarcloud.io/dashboard?id=heutelbeck_sapl-policy-engine
[security-rating-shield]: https://sonarcloud.io/api/project_badges/measure?project=heutelbeck_sapl-policy-engine&metric=security_rating
[security-rating-url]: https://sonarcloud.io/summary/new_code?id=heutelbeck_sapl-policy-engine
[maven-central-shield]: https://img.shields.io/maven-central/v/io.sapl/sapl-lang
[maven-central-url]: https://mvnrepository.com/artifact/io.sapl

[website-url]: https://sapl.io
[demos-url]: https://github.com/heutelbeck/sapl-demos
[sbom-definition-url]: https://www.cisa.gov/sbom
[sbom-extraction-url]: https://github.com/heutelbeck/sapl-policy-engine/network/dependencies
