---
layout: default
title: Java API
#permalink: /reference/Java-API/
parent: PDP APIs
grand_parent: SAPL Reference
nav_order: 3
---

## Java API

The Java API is based on the reactive libraries of Project Reactor (<https://projectreactor.io/>). The API is defined in the `sapl-api` module:

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-api</artifactId>
      <version>4.0.0-SNAPSHOT</version>
   </dependency>
```

The key interface is the `PolicyDecisionPoint` exposing methods matching the PDP server API:

```java
/**
 * The policy decision point is the component in the system, which will take an
 * authorization subscription, retrieve matching policies from the policy
 * retrieval point, evaluate the policies while potentially consulting external
 * resources (e.g., through attribute finders), and return a {@link Flux} of
 * authorization decision objects.
 *
 * This interface offers methods to hand over an authorization subscription to
 * the policy decision point, differing in the construction of the
 * underlying authorization subscription object.
 */
public interface PolicyDecisionPoint {

    /**
     * Takes an authorization subscription object and returns a {@link Flux}
     * emitting matching authorization decisions.
     *
     * @param authzSubscription the SAPL authorization subscription object
     * @return a {@link Flux} emitting the authorization decisions for the given
     *         authorization subscription. New authorization decisions are only
     *         added to the stream if they are different from the preceding
     *         authorization decision.
     */
    Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription);

    /**
     * Takes an authorization subscription object and returns a {@link Mono}
     * emitting the first matching authorization decision.
     *
     * @param authzSubscription the SAPL authorization subscription object
     * @return an authorization decisions for the given authorization subscription.
     */
    default Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authzSubscription) {
        return Mono.from(decide(authzSubscription));
    }
    
    /**
     * Multi-subscription variant of {@link #decide(AuthorizationSubscription)}.
     *
     * @param multiAuthzSubscription the multi-subscription object containing the
     *                               subjects, actions, resources, and environments
     *                               of the authorization subscriptions to be
     *                               evaluated by the PDP.
     * @return a {@link Flux} emitting authorization decisions for the given
     *         authorization subscriptions as soon as they are available. Related
     *         authorization decisions and authorization subscriptions have the same
     *         id.
     */
    Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription);

    /**
     * Multi-subscription variant of {@link #decide(AuthorizationSubscription)}.
     *
     * @param multiAuthzSubscription the multi-subscription object containing the
     *                               subjects, actions, resources, and environments
     *                               of the authorization subscriptions to be
     *                               evaluated by the PDP.
     * @return a {@link Flux} emitting authorization decisions for the given
     *         authorization subscriptions as soon as at least one authorization
     *         decision for each authorization subscription is available.
     */
    Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription);

}
```

### Embedded PDP (Non-Spring)

For non-Spring applications, an embedded PDP can be used directly:

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-pdp</artifactId>
      <version>4.0.0-SNAPSHOT</version>
   </dependency>
```

### Remote PDP Client (Non-Spring)

For non-Spring applications connecting to a remote PDP server:

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-pdp-remote</artifactId>
      <version>4.0.0-SNAPSHOT</version>
   </dependency>
```

### Spring Boot Applications

For Spring Boot applications, use the unified starter which includes the embedded PDP, remote PDP client, Spring Security integration, and all autoconfiguration:

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-spring-boot-starter</artifactId>
      <version>4.0.0-SNAPSHOT</version>
   </dependency>
```

By default, the embedded PDP is active. To use a remote PDP server instead, configure the following properties:

```properties
io.sapl.pdp.remote.enabled=true
io.sapl.pdp.remote.host=https://your-pdp-server:8443
io.sapl.pdp.remote.key=your-client-key
io.sapl.pdp.remote.secret=your-client-secret
```

#### Reducing Application Footprint

When using only a remote PDP, you can exclude the embedded PDP dependency to reduce the application size:

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-spring-boot-starter</artifactId>
      <version>4.0.0-SNAPSHOT</version>
      <exclusions>
         <exclusion>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-pdp</artifactId>
         </exclusion>
      </exclusions>
   </dependency>
```
