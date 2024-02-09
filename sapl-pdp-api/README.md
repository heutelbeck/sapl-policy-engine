# Java Policy Decision Point API

The Java API is based on the reactive libraries of [Project Reactor](https://projectreactor.io). The API is defined in the `sapl-pdp-api` module:

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-pdp-api</artifactId>
      <version>{project-version}</version>
   </dependency>
```

# API

Note: This API is the raw PDP API. This means this API is used by developers attempting to implement their own Policy Enforcement Points (PEPs) or by SAPL framework integration libraries. 

If you are not implementing a PEP yourself, this API is still relevant, as it abstracts away the specific PDP implementation and the SAPL framework integration libraries, i.e., Spring Security, Axon, Vaadin, are implemented against this API. 
Whenever using any of the integration libraries, the user needs to specify the actual implementation to be used. There are four implementations supplied:

## Pure Java Embedded PDP

An embedded PDP without specific framework requirements. 

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-pdp-embedded</artifactId>
      <version>{sapl-version}</version>
   </dependency>
```

## Spring Boot Embedded PDP

An embedded PDP with Spring Boot auto configuration support.

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-spring-pdp-embedded</artifactId>
      <version>{sapl-version}</version>
   </dependency>
```

## Pure Java Remote PDP

A client library for connecting to a dedicated PDP Server, e.g., SAPL Server LE or CE.

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-pdp-remote</artifactId>
      <version>{sapl-version}</version>
   </dependency>
```

## Spring Boot Remote PDP

A Spring Boot wrapper around the Remote PDP implementation with Spring Boot auto configuration support.

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-spring-pdp-remote</artifactId>
      <version>{sapl-version}</version>
   </dependency>
```
