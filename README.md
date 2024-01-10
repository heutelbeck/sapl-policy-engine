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
[![Maven Snapshots][snapshot-shield]][snapshot-url]
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/heutelbeck/sapl-policy-engine/badge)](https://securityscorecards.dev/viewer/?uri=github.com/heutelbeck/sapl-policy-engine)

<!-- ABOUT THE PROJECT -->
## About The Project

The reactive open-source engine for adding Attribute-Based Access Control (ABAC) to your Java applications, supporting attribute streams for efficient interactive real-time access control.

SAPL is a powerful policy language and engine for implementing ABAC. It comes with development tools for testing, authorization servers, and authoring tools. Framework integrations are available for Spring, Axon, and Vaadin to provide flexible policy enforcement points (PEPs) in your application.

For an explanation, overview, and documentation about the SAPL project look up our [website][website-url].

<!-- GETTING STARTED -->
## Getting Started

To get started with integrating SAPL into your Java application, add the following reference to your build tool project definition.

**We recommend using the [SNAPSHOT](#snapshots) version as the latest stable version is outdated.**

**Maven**

```xml
<dependency>
  <groupId>io.sapl</groupId>
  <artifactId>sapl-pdp-embedded</artifactId>
  <version>2.0.1</version>
</dependency>
```

**Gradle**

```gradle
dependencies {
  implementation 'io.sapl:sapl-pdp-embedded:2.0.1'
}
```

This enables you to use the interface `PolicyDecisionPoint` to decide about requests.

Want to integrate and understand the full scale of capabilities of SAPL? Visit our [website][website-url].

Want to see integration examples? View our dedicated [demos](#want-to-integrate-sapl).

Feeling experimental? Use our [snapshots](#snapshots) for the newest development state!

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
...
<dependencies>
  <dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-{package}</artifactId>
    <version>3.0.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

**Gradle**

```gradle
repositories {
  maven {
    url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
  }
}

dependencies {
  implementation 'io.sapl:sapl-{package}3.0.0-SNAPSHOT'
}
```

**You need to replace `{package}` with the designated project!**


<!-- CODE OF CONDUCT -->
## Code of Conduct

This project has adopted a [Code of Conduct](CODE_OF_CONDUCT.md) and it will be enforced in any communication.

<!-- LICENSE -->
## License

Distributed under the Apache 2.0 License. See [LICENSE.md](./LICENSE.md) for more information.

<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[build-status-shield]: https://github.com/heutelbeck/sapl-policy-engine/actions/workflows/build_master.yml/badge.svg
[build-status-url]: https://github.com/heutelbeck/sapl-policy-engine/actions/workflows/build_master.yml
[sonarcloud-status-shield]: https://sonarcloud.io/api/project_badges/measure?project=heutelbeck_sapl-policy-engine&metric=alert_status
[sonarcloud-status-url]: https://sonarcloud.io/dashboard?id=heutelbeck_sapl-policy-engine
[security-rating-shield]: https://sonarcloud.io/api/project_badges/measure?project=heutelbeck_sapl-policy-engine&metric=security_rating
[security-rating-url]: https://sonarcloud.io/summary/new_code?id=heutelbeck_sapl-policy-engine
[maven-central-shield]: https://img.shields.io/maven-central/v/io.sapl/sapl-lang
[maven-central-url]: https://mvnrepository.com/artifact/io.sapl
[snapshot-shield]: https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fs01.oss.sonatype.org%2Fcontent%2Frepositories%2Fsnapshots%2Fio%2Fsapl%2Fsapl-policy-engine%2Fmaven-metadata.xml
[snapshot-url]: https://s01.oss.sonatype.org/content/repositories/snapshots/io/sapl

[website-url]: https://sapl.io
[demos-url]: https://github.com/heutelbeck/sapl-demos
[sbom-definition-url]: https://www.cisa.gov/sbom
[sbom-extraction-url]: https://github.com/heutelbeck/sapl-policy-engine/network/dependencies