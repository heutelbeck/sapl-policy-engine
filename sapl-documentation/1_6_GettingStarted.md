---
layout: default
title: Getting Started
#permalink: /reference/Getting-Started/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 6
---

## Getting Started

To learn the SAPL policy language and check out some example policies, the [SAPL-Playground](https://playground.sapl.io/) offers a tool for safe experimentation.

In addition, SAPL provides an embedded PDP, including an embedded PRP with a file system policy store that seamlessly integrates into Java applications. Besides this guide, the quickest way to start is to build upon the demo projects hosted on [GitHub](https://github.com/heutelbeck/sapl-demos). Some good demos to start with are the simple no-framework [Embedded PDP Demo](https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-embedded) or the full-stack [Spring MVC Project](https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-mvc-app) or the [Fully reactive Webflux Application](https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-webflux).

### Maven Dependencies

- SAPL requires Java 11 or newer and is compatible with Java 17.

  ```xml
  <properties> <java.version>11</java.version> <maven.compiler.source>${java.version}</maven.compiler.source> <maven.compiler.target>${java.version}</maven.compiler.target> </properties>
  ```

- Add a SAPL dependency to the application. When using Maven one can add the following dependencies to the project’s `pom.xml`:

  ```xml
  <dependency> <groupId>io.sapl</groupId> <artifactId>sapl-pdp-embedded</artifactId> <version>3.0.0-SNAPSHOT</version> </dependency>
  ```

- Add the Maven Central snapshot repository to the `pom.xml`:

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

- If more SAPL dependencies are expected to be used, a useful bill of materials POM is offered, centralizing the dependency management for SAPL artifacts:

  ```xml
  <dependencyManagement> <dependencies> <dependency> <groupId>io.sapl</groupId> <artifactId>sapl-bom</artifactId> <version>3.0.0-SNAPSHOT</version> <type>pom</type> <scope>import</scope> </dependency> </dependencies> </dependencyManagement>
  ```

### Coding

1. In the application, create a new `EmbeddedPolicyDecisionPoint`. The argument `"~/sapl"` specifies the directory that contains the configuration file `pdp.json` and all policies (i.e., files ending with `.sapl`).

   ```java
   EmbeddedPolicyDecisionPoint pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("~/sapl");
   ```

2. Add a `pdp.json` with the following content to the directory "~/sapl":

   ```json
   {
       "algorithm": "DENY_UNLESS_PERMIT",
       "variables": {}
   }
   ```

3. Add some policy sets or policies to `"~/sapl"`. Both policy sets and policies are files with the extension `.sapl`. For example, add the following policy:

   ```
   policy "test_policy"
   permit subject == "admin"
   ```
4. Obtain a decision using the PDP’s `decide` method.

   ```java
   var authzSubscription = AuthorizationSubscription.of("admin", "an_action", "a_resource");
   Flux<AuthorizationDecision> authzDecisions = pdp.decide(authzSubscription);
   authzDecisions.subscribe(authzDecision -> System.out.println(authzDecision.getDecision()));
   ```

The console output should be `PERMIT`. With subject set to `"alice"` instead of `"admin"`, the output should be `DENY`.

Note at runtime the policies can be modified. Adding or removing polices can immediately trigger a change in the decisions.