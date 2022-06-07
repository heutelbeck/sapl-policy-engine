# SAPL Spring Security Integration

This package provides integration into Spring Security. It provides a number of infrastructure Beans to establish Policy
Enforcement Points within a an application.

## Dependencies

To use this package two dependencies must be declared. First, this package, and second a Policy Decision Point
implementation has to be selected.

```xml

<!-- Import the SAPL 'Bill Of Materials', or BOM, package. This takes care of setting consistent versions for all SAPL Dependencies. -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-bom</artifactId>
            <version>2.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
<dependency>
    <!-- This is the package for integrating directly with Spring Security mechanisms -->
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-security</artifactId>
</dependency>

<!-- Select a Policy Decision Point (PDP) implementation. Select either an embedded or a remote PDP. 
     You must not declare both dependencies at the same time. -->
<dependency>
    <!-- Select this package to bundle an embedded PDP with you application, loading policies 
         from the file-system, or from the resources packaged with your application. 
         This is a good PDP to start with for your first SAPL application as it does not 
         require a dedicated PDP server running in the infrastructure -->
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-pdp-embedded</artifactId>
</dependency>
<dependency>
    <!-- Select this package to use a dedicated remote PDP, e.g., sapl-server-lt.  -->
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-pdp-remote</artifactId>
</dependency>
</dependencies>

        <!-- If you are using a SNAPSHOT version, pleas add the snapshot repository. Else, this can be omitted. -->
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

## Configuration

### PDP

The package provides auto configuration for the PDP. Depending on the selected PDP implementation, folders, host,
credentials can be configured using the matching properties.

### Method Security

Similar to Spring Security itself, the SAPL integration supports both blocking and reactive method security annotations.
These mechanisms hook directly into the respective Spring Security classes and have to be configured accordingly.

Just like in Spring Security, method security is not active by default and one of the two modes has to be activated.
While in Spring Security it is sufficient to add the annotations `@EnableGlobalMethodSecurity`
or `@EnableReactiveMethodSecurity` on any configuration class, a specific configuration class has to be provided to
activate the SAPL Annotations alongside the Spring Security method security annotations.

Important note: If Spring Security annotations like `@PreAuthorize` and `@PostAuthorize` are applied to the same method
as SAPL Annotations like `@PreEnforce`, `@PostEnforce`, `@Enforce` only one type of annotation will be applied.
Currently, legacy annotations override SAPL annotations. However, the SAPL libraries make no guarantees that this will
be maintained. Thus, these types of annotations must not be used on the same method. This also holds true for
annotations which are applicable to a method through class annotations or inheritance.

#### Non-Reactive/Blocking Method Security PEP

To activate blocking Spring Security annotations and the SAPL annotations `@PreEnforce`and `@PostEnforce` add the
configuration class `MethodSecurityConfiguration` to your application. If your application customizes the
Spring `GlobalMethodSecurityConfiguration` by sub-classing it, use this class as the parent class instead.

```java
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintHandlerService;

@Configuration
@EnableGlobalMethodSecurity
public class MethodSecurityConfiguration extends SaplMethodSecurityConfiguration {

    public MethodSecurityAutoConfiguration(ObjectFactory<PolicyDecisionPoint> pdpFactory,
                                           ObjectFactory<ConstraintHandlerService> constraintHandlerFactory,
                                           ObjectFactory<ObjectMapper> objectMapperFactory) {
        super(pdpFactory, constraintHandlerFactory, objectMapperFactory);
    }

}
```

# Reactive Method Security PEP

To activate reactive Spring Security annotations and the SAPL ..l add the following configuration class to your
application.

```java
/**
 * Provide the @EnableReactiveSaplMethodSecurity annotation on any configuration
 * class to activate the reactive method security for methods returning a
 * Publisher<?>.
 */
@EnableWebFluxSecurity
@EnableReactiveSaplMethodSecurity
public class SecurityConfiguration {
	@Bean
	public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
		// @formatter:off
		return http.authorizeExchange()
				   .anyExchange()
				   .permitAll()
				   .and().build();
		// @formatter:off

	}
}
```

## `@PreEnforce`

The `@PreEnforce` annotation wraps the `Mono<>` or `Flux<>` returned by the method with
a Policy Enforcement Point.

The access control only starts when a subscriber subscribes to the wrapped `Publisher<>`, not at the construction time of the `Publisher<>` object.
 
Before allowing the subscriber to access the original `Publisher<>`, the PEP
constructs an AuthorizationSubscription and sends it to the PDP deployed in
the infrastructure. The PEP consumes exactly one decision and then cancels
its subscription to the PDP.

If the decision contained constraints, i.e., advice or obligations, them the
PEP hooks the execution of the constraint handling into the matching signal
handlers of the reactive stream, e.g., onSubscription, onNext, onError etc.

This means that constraints contained within the one decision made by the
PDP are enforced continuously throughout the lifetime of the reactive stream.
E.g., a constrained hooked into the onNext signal path will be triggered on
every data item published on the stream.

If you want to be able to react to changing decisions throughout the lifetime of the reactive stream, consider using the @Enforce annotation instead.

The `@PreEnforce` annotation can be combined with a @PostEnforce annotation, only if the Publisher is of type `Mono<?>`. It cannot be combined with other `@EnforceX` annotations on the same method. Also, it cannot be combined with Spring security method security annotations, e.g., `@PreAuthorize`.

## `@PostEnforce`

The `@PostEnforce` annotation is typically used if the return object of a
a protected method is required to make the decision, or if the return object can be modified via a transformation statement in a policy.
 
As an AuthorizationSubscription has to be constructed supplying the resource to be modified, and this value has to be well-defined, this annotation is only applicable to methods returning a `Mono<>`.

By adding the SpEL expression `resource="returnObject"` to the
annotation has the effect to tell the PEP to set the return object of the
Mono as the resource value of the AuthorizationSubscription to the PDP.

Please note that in the AuthorizationSubscription the object has to be
marshaled to JSON. For this to work, one has to ensure that the default
Jackson ObjectMapper, in the application context, knows to do this for the given type. Thus, it may be necessary to deploy matching custom serializers or to annotate the class with the matching Jackson annotations.


## `@EnforceTillDenied`

The `@EnforceTillDenied` annotation wraps the `Flux<>` in a PEP.

The access control only starts when a subscriber subscribes to the wrapped
`Flux<>`, not at construction time of the `Flux<>`.

The basic concept of the `@EnforceTillDenied` PEP is to grant access to the
Flux<> upon an initial `PERMIT` decision and to grant access until a non-`PERMIT` decision is received.

Upon the initial `PERMIT`, the PEP subscribes to the original Flux<>. During access to the `Flux<>`, all constraints are enforced.

Upon receiving a new `PERMIT` decision with different constraints, the
constraint handling is updated accordingly.

Upon receiving a non-`PERMIT` decision, the final constraints are enforced, and an AccessDeniedException ends the `Flux<>`.

The `@EnforceTillDenied` annotation cannot be combined with any other
enforcement annotation.

## `@EnforceDropWhileDenied`

The `@EnforceDropWhileDenied` annotation wraps the `Flux<>` in a PEP.

The access control only starts when a subscriber subscribes to the wrapped
`Flux<>`, not at construction time of the `Flux<>`.

The basic concept of the `@EnforceDropWhileDenied` PEP is to grant access to
the `FLux<>` upon an initial `PERMIT` decision and to grant access until the
client cancels the subscription, or the original `Flux<>` completes. However,
whenever a non-`PERMIT` decision is received, all messages are dropped from the
`Flux<>` until a new `PERMIT` decision is received.

The subscriber will not be made aware of the fact that events are dropped
from the stream.

Upon the initial `PERMIT`, the PEP subscribes to the original `Flux<>`. During
access to the `Flux<>`, all constraints are enforced.

Upon receiving a new `PERMIT` decision with different constraints, the
constraint handling is updated accordingly.

Upon receiving a non-`PERMIT` decision, the constraints are enforced, and
messages are dropped without sending an `AccessDeniedException` downstream. The
date resumes on receiving a new `PERMIT` decision.

The `@EnforceDropWhileDenied` annotation cannot be combined with any other
enforcement annotation.

## `EnforceRecoverableIfDenied` 

The `@EnforceRecoverableIfDenied` annotation wraps the `Flux<>` in a PEP.

The access control only starts when a subscriber subscribes to the wrapped
`Flux<>`, not at construction time of the `Flux<>`.

The basic concept of the @EnforceRecoverableIfDenied PEP is to grant access
to the `FLux<>` upon an initial `PERMIT` decision and to grant access until the
client cancels the subscription, or the original `Flux<>` completes. However,
whenever a non-`PERMIT` decision is received, all messages are dropped from the
`Flux<>` until a new `PERMIT` decision is received.

The subscriber will be made not be made aware of the fact that events are
dropped from the stream by sending `AccessDeniedExceptions` on a non-`PERMIT`
decision.

The subscriber can then decide to stay subscribed via `.onErrorContinue()`.
Without .onErrorContinue this behaves similar to `@EnforceTillDenied`. With
`.onErrorContinue()` this behaves similar to `@EnforceDropWhileDenied`, however
the subscriber can explicitly handle the event that access is denied and
choose to stay subscribed or not.

The `@EnforceRecoverableIfDenied` annotation cannot be combined with any other enforcement annotation.




