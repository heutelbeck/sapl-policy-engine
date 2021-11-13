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

The key interface is the `PolicyDecisionPoint` exposing methods matching the PDP server HTTP SSE API:

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

## Embedded PDP

To use a PDP two implementations of the API are supplied. First, a completely embedded PDP can be used to be deployed with an application. (See: <https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-pdp-embedded>)

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-pdp-embedded</artifactId>
      <version>{project-version}</version>
   </dependency>
```

The library with Spring auto configuration support:

```xml
   <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-spring-pdp-embedded</artifactId>
      <version>{project-version}</version>
   </dependency>
```
