# SAPL Spring Security Integration

This package provides a deep integration with Spring Security. It provides a number of infrastructure Beans to establish Policy Enforcement Points within an application, using a declarative aspect-oriented programming style.

It also enables the use of so-called constraints and the implementation of suitable side effects.

## Features

Spring Security is a framework that provides [authentication](https://docs.spring.io/spring-security/reference/features/authentication/index.html), [authorization](https://docs.spring.io/spring-security/reference/features/authorization/index.html), and [protection against common attacks](https://docs.spring.io/spring-security/reference/features/exploits/index.html). With first-class support for securing [imperative](https://docs.spring.io/spring-security/reference/servlet/index.html) and [reactive](https://docs.spring.io/spring-security/reference/reactive/index.html) applications, it is the de facto standard for securing Spring-based applications. SAPL Spring Security Integration focuses on authorization. Authorization determines who is allowed to access a particular resource. Spring Security provides defence in depth by allowing for request and method-based authorization.

## Getting the SAPL Spring Security Extension

### Usage with Maven

To ensure consistent versions for all SAPL dependencies required, SAPL provides a 'Bill Of Materials' (BOM) module. This way, you do not need to declare the `<version>` of each SAPL dependency.

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

Add the snapshot repository if you are using a SNAPSHOT version of SAPL. Otherwise, you can omit it.

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

You can add the SAPL Spring Security integration to your application by adding the following dependency.

```xml
<dependencies>
    <!-- ... other dependency elements ... -->
    <dependency>
        <groupId>io.sapl</groupId>
        <artifactId>sapl-spring-security</artifactId>
    </dependency>
</dependencies>
```

In addition, one of the two Policy Decision Point (PDP) implementations has to be selected. You can embed the PDP in your application or use a dedicated remote PDP Server, e.g., sapl-server-lt.

```xml
<dependencies>
    <!-- ... other dependency elements ... -->
    <!-- choose only one -->
    <dependency>
        <groupId>io.sapl</groupId>
        <artifactId>sapl-spring-pdp-embedded</artifactId>
    </dependency>

    <dependency>
        <groupId>io.sapl</groupId>
        <artifactId>sapl-spring-pdp-remote</artifactId>
    </dependency>
</dependencies>
```

## Project Modules and Dependencies

We recommend that you review the `pom.xml` files to understand third-party dependencies and versions, even if you are not using Maven.

### sapl-pdp-api

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>sapl-pdp-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

This module contains the raw PDP API, which is used by developers attempting to implement their Policy Enforcement Points (PEPs) or SAPL framework integration libraries.

### spring-boot-autoconfigure

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
```

This module automatically configures your Spring application based on the added jar dependencies.

### spring-security-config

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-config</artifactId>
</dependency>
```

This module contains the security namespace parsing code and Java configuration code. You need it if you use the Spring Security XML namespace for configuration or Spring Security’s Java Configuration support. The main package is `org.springframework.security.config`. None of the classes are intended for direct use in an application.

| Dependency | Version | Description |
|------------|---------|-------------|
| spring-security-core |  |  |
| spring-security-web |  | Required if you are using any web-related namespace configuration (optional). |
| spring-security-ldap |  | Required if you are using the LDAP namespace options (optional). |
| aspectjweaver | 1\.6.10 | Required if using the protect-pointcut namespace syntax (optional). |

### spring-security-web

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-web</artifactId>
</dependency>
```

This module contains filters and related web security infrastructure code. It contains anything with a servlet API dependency. You need it if you require Spring Security web authentication services and URL-based access control. The main package is `org.springframework.security.web`.

| Dependency | Version | Description |
|------------|---------|-------------|
| spring-security-core |  |  |
| spring-security-web |  | Required for clients that use HTTP remoting support. |
| spring-jdbc |  | Required for a JDBC-based persistent remember-me token repository (optional). |
| spring-tx |  | Required by remember-me persistent token repository implementations (optional). |

### jackson-datatype-jsr310

```xml
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

### jakarta.servlet-api

```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```

### json-path

```xml
<dependency>
    <groupId>com.jayway.jsonpath</groupId>
    <artifactId>json-path</artifactId>
</dependency>
```

JSON (JavaScript Object Notation) is a text-based, language-independent format that is easily understandable by humans and machines. JsonPath expressions always refer to a JSON structure in the same way as XPath expression are used in combination with an XML document. The "root member object" in JsonPath is always referred to as $ regardless if it is an object or array.

### guava

```xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
</dependency>
```

Guava is a suite of core and expanded libraries that include utility classes, Google's collections, I/O classes, and much more.

### lombok

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

Lombok is a Java library that provides annotations to simplify Java development by automating boilerplate code generation. Key features include the automatic generation of getters, setters, equals, hashCode, and toString methods, as well as a facility for automatic resource management. It aims to reduce the amount of manual coding, thereby streamlining the codebase and reducing the potential for errors. Lombok is implemented to eliminate some boilerplate code.

### slf4j-api

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>
```

This module contains the API for SLF4J (The Simple Logging Facade for Java), which serves as a simple facade or abstraction for various logging frameworks. It allows the end user to plug in the desired logging framework at deployment time.

## Servlet Applications

### Dependencies

The following additional dependency is required for servlet applications:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

This creates a servlet-based environment. This ensures that the numerous autoconfiguration classes provided for servlet applications are loaded automatically.

### Authenticating the application

You can access the application at localhost:8080/, which will redirect the browser to the default login page. You can provide the default username  `user` with the randomly generated password logged to the console. The browser is then taken to the initially requested page.

To log out you can visit [localhost:8080/logout](http://localhost:8080/logout) and then confirming you wish to log out.

### Authorization

By default, SAPL Spring Security’s authorization will require all requests to be authenticated.
The explicit configuration for servlet applications looks like:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
	...

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                   .httpBasic(Customizer.withDefaults())
        	       .formLogin(Customizer.withDefaults())
                   .build();
    }
}
```

#### Authorization Architecture

The SAPL Spring Security integration provides its custom `SaplAuthorizationManager` for the HTTP filter chain.
Its `check` method looks as follows:

```java
AuthorizationDecision check(Supplier<Authentication> authenticationSupplier, RequestAuthorizationContext requestAuthorizationContext) {
    ...
}
```

A PDP makes a final `AuthorizationDecision` based on an `AuthorizationSubscription` generated from the transferred values and any constraints.

#### Authorize HttpServletRequests

The SAPL Spring Security Integration makes it possible to extend the modelling at the request level in the following way:

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http, SaplAuthorizationManager saplAuthzManager) throws Exception {
    // @formatter:off
    return http.authorizeHttpRequests(requests -> requests.anyRequest().access(saplAuthzManager))
               .build();
    // @formatter:on
}
```

This instructs the `SaplAuthorizationManager` to check the HTTP request.
Suitable authorization policies can now be formulated, but only based on the information in the HTTP request.

#### Method Security

In addition, SAPL supports modelling at the method level.

You can activate blocking method security in your application by annotating any `@Configuration` class with `@EnableSaplMethodSecurity`.

```java
@Configuration
@EnableWebSecurity
@EnableSaplMethodSecurity
public class SecurityConfiguration {
	...
}
```

This makes it possible to use the annotations `@PreEnforce` and `@PostEnforce` to add policy enforcement points to methods and classes. These annotations can be extended with parameters to create a custom authorization subscription using the Spring Expression Language (SpEL).

##### @PreEnforce

The `@PreEnforce` annotation places a PEP before the method execution.

```java
public interface BookRepository {
	
	@PreEnforce
	Iterable<Book> findAll();

}
```

This ensures the method is only executed if the PDP makes a `PERMIT` decision based on the authorization subscription.

If no custom authorization subscription is defined, the PEP gathers as much information as possible to describe the three required objects (subject, action, resource) for an authorization subscription. This is mainly unnecessary and contains a lot of redundant information.

A custom authorization subscription could look like this:

```java
public interface BookRepository {
	
	@PreEnforce(subject = "authentication.getPrincipal()", action = "'list books'")
	Iterable<Book> findAll();

}
```

The parameter `subject = "authentication.getPrincipal()"` extracts the principal object from the authentication object and uses it as the subject object in the subscription.

The parameter `action = "'list books'"` sets the action object in the subscription to the string constant `list books`.

##### @PostEnforce

The `@PostEnforce` annotation places a PEP after the method execution.

This annotation is typically used if the return object of a protected method is required to make the decision or if the return object can be modified via a transformation statement in a policy.

```java
public interface BookRepository {
	
	@PostEnforce(subject = "authentication.getPrincipal()", action = "'read book'", resource = "returnObject")
	Optional<Book> findById(Long id);

}
```

The parameter `resource = "returnObject"` tells the PEP to set the resource object in the subscription to the method invocation result.

###### Multiple Annotations Are Computed In Series

SAPL supports combining `@PreEnforce` and `@PostEnforce`  on a single method. Both annotations must return `PERMIT` for the method's consumer to receive the invocation result.

###### Repeated Annotations Are Not Supported

It is not possible to use the same annotation twice on the same method, e.g. you cannot place `@PreEnforce` twice.

## Reactive Applications

### Dependencies

The following additional dependency is required for reactive applications:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

This creates a Webflux-based environment. This ensures that the numerous autoconfiguration classes for reactive applications are automatically loaded.

### Authenticating the application

You can access the application at localhost:8080/, which will redirect the browser to the default login page. You can provide the default username `user` with the randomly generated password logged to the console. The browser is then taken to the initially requested page.

To log out, you can visit [localhost:8080/logout](http://localhost:8080/logout) and confirm that you wish to log out.

### Authorization

By default, SAPL Spring Security’s authorization will require all requests to be authenticated. The explicit configuration for reactive applications looks like this:

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {
	// ...
    @Bean
	SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
		return http.authorizeExchange(exchange -> exchange.anyExchange().authenticated())
				   .formLogin(withDefaults())
				   .httpBasic(withDefaults())
				   .build();
	}
}
```

#### Authorization Architecture

The SAPL Spring Security integration also provides its custom `ReactiveSaplAuthorizationManager` for the reactive Spring Security web filter chain.
Its `check` method looks as follows:

```java
Mono<org.springframework.security.authorization.AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
    ...
}
```

A PDP makes a final `AuthorizationDecision` based on an `AuthorizationSubscription`, generated from the `Authentication` and the `AuthorizationContext`, and any constraints.

#### Authorize ServerHttpRequest

The SAPL Spring Security Integration makes it possible to extend the modeling at the request level in the following way:

```java
@Bean
SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, ReactiveSaplAuthorizationManager saplAuthzManager) {
    return http.authorizeExchange(exchange -> exchange.anyExchange().access(saplAuthzManager))
               .formLogin(withDefaults())
               .httpBasic(withDefaults())
               .build();
}
```

This instructs the `ReactiveSaplAuthorizationManager` to check the HTTP request. Suitable authorization policies can now be formulated, but only based on the information in the HTTP request.

#### Reactive Method Security

You can activate reactive method security in your application by annotating any `@Configuration` class with `@EnableReactiveSaplMethodSecurity`.

```java
@Configuration
@EnableWebFluxSecurity
@EnableReactiveSaplMethodSecurity
public class SecurityConfiguration {
	...
}
```

This makes it possible to use the annotations `@PreEnforce`, `@PostEnforce`, `@EnforceTillDenied`, `@EnforceDropWhileDenied` and `@EnforceRecoverableIfDenied` to add reactive policy enforcement points to methods and classes. These annotations can be extended with parameters to create a custom authorization subscription using the Spring Expression Language (SpEL).

**Note:** A reactive policy enforcement point is only applicable to methods returning a `Publisher<>`, i.e. a `Mono<>` or a `Flux<>`.

##### @PreEnforce

The `@PreEnforce` annotation wraps the `Mono<>` or `Flux<>` returned by the method with a Policy Enforcement Point.

```java
public interface BookRepository {

    @PreEnforce
    Flux<Book> findAll();
}
```

Before allowing the subscriber to access the original `Publisher<>`, the PEP constructs an `AuthorizationSubscription` and sends it to the PDP deployed in the infrastructure. The PEP consumes exactly one decision and then cancels its subscription to the PDP.

Suppose the decision contained constraints, i.e., obligations or advice. In that case, the PEP hooks the execution of the constraint handling into the matching signal handlers of the reactive stream, e.g., onSubscription, onNext, onError, etc.

This means that constraints contained within the one decision made by the PDP are enforced continuously throughout the reactive stream's lifetime. For example, a constrained hook into the onNext signal path will be triggered on every data item published on the stream.

If you want to be able to react to changing decisions throughout the lifetime of the reactive data stream, you should use one of the `@Enforce...` annotations instead.

##### @PostEnforce

The `@PostEnforce` annotation is typically used if the return object of a protected method is required to make the decision or if the return object can be modified via a transformation statement in a policy.

```java
public interface BookRepository {

    @PostEnforce(resource = "returnObject")
    Mono<Book> findById(Long id);
}
```

As an `AuthorizationSubscription` has to be constructed, supplying the resource to be modified, and this value has to be well-defined. This annotation is only applicable to methods returning a `Mono<>`.

Adding the SpEL expression `resource="returnObject"` to the annotation has the effect of telling the PEP to set the return object of the `Mono<>` as the resource value of the `AuthorizationSubscription` to the PDP.

Please note that in the `AuthorizationSubscription` the object has to be marshalled to JSON. For this to work, one has to ensure that the default Jackson `ObjectMapper`, in the application context, knows to do this for the given type. Thus, it may be necessary to deploy matching custom serializers or to annotate the class with the matching Jackson annotations.

##### @EnforceTillDenied

The `@EnforceTillDenied` annotation wraps the `Flux<>` in a PEP.

The basic concept of the `@EnforceTillDenied` PEP is to grant access to the `Flux<>` upon an initial `PERMIT` decision until a non-`PERMIT` decision is received.

After the initial `PERMIT`, the PEP subscribes to the original `Flux<>`. While accessing the `Flux<>`, all constraints are enforced.

Upon receiving a new `PERMIT` decision with different constraints, the constraint handling is updated accordingly.

Upon receiving a non-`PERMIT` decision, the final constraints are enforced and an `AccessDeniedException` terminates the `Flux<>`.

##### @EnforceDropWhileDenied

The `@EnforceDropWhileDenied` annotation wraps the `Flux<>` in a PEP.

The basic concept of the `@EnforceDropWhileDenied` PEP is to grant access to the `FLux<>` upon an initial `PERMIT` decision until the client cancels the subscription or the original `Flux<>` completes. However, whenever a non-`PERMIT` decision is received, all messages are dropped from the `Flux<>` until a new `PERMIT` decision is received.

The subscriber will be unaware that events are dropped from the stream.

After the initial `PERMIT`, the PEP subscribes to the original `Flux<>`. While accessing the `Flux<>`, all constraints are enforced.

Upon receiving a new `PERMIT` decision with different constraints, the constraint handling is updated accordingly.

Upon receiving a non-`PERMIT` decision, the constraints are enforced, and messages are dropped without sending an `AccessDeniedException` downstream. Access is granted again as soon as a new `PERMIT` decision is received.

##### @EnforceRecoverableIfDenied

The `@EnforceRecoverableIfDenied` annotation wraps the `Flux<>` in a PEP.

The basic concept of `@EnforceRecoverableIfDenied` is almost the same as `@EnforceDropWhileDenied` with a small difference

The subscriber will be made aware of the fact that events are dropped from the stream by sending `AccessDeniedExceptions` on a non-`PERMIT` decision.

The subscriber can then decide to stay subscribed via `.onErrorContinue()`. Without `.onErrorContinue()` this behaves similarly to `@EnforceTillDenied`. With `.onErrorContinue()` this behaves similarly to `@EnforceDropWhileDenied`. However, the subscriber can explicitly handle the event that access is denied and can choose to stay subscribed.

##### Access Control On Subscription

The access control only starts when a subscriber subscribes to the wrapped `Publisher<>`, not at the construction time of the `Publisher<>`.

##### Multiple Annotations Are Computed In Series

SAPL Spring Security supports multiple method security annotations, but only with restrictions. For reactive applications, only the annotations `@PreEnforce` and `@PostEnforce` can be used in combination with a method. Additionally, the publisher must be of type Mono<>. For an invocation to be authorized, both annotation inspections need to pass authorization.

The following combinations are **NOT** supported:

###### SAPL And Spring Annotations

It is not possible to annotate a method with both at least one SAPL annotation (@PreEnforce, @PostEnforce, @Enforce...) and at least one Spring method security annotation (@PreAuthorize, @PostAuthorize, @PreFilter, @PostFilter).

###### @Pre-/@PostEnforce And @Enforce...

It is not possible to annotate a method with at least one of `@PreEnforce` or `@PostEnforce` and one of `@EnforceTillDenied`, `@EnforceDropWhileDenied` or `@EnforceRecoverableIfDenied`.

###### More Than One Enforce...

It is not possible to annotate a method with more than one of `@EnforceTillDenied`, `@EnforceDropWhileDenied` or `@EnforceRecoverableIfDenied`.

##### Repeated Annotations Are Not Supported

It is not possible to use the same annotation twice on the same method, e.g. you cannot place `@PreEnforce` twice.

## Constraint Handler

#### Constraints

In SAPL, decisions can include additional requirements for the PEP to enforce beyond simply granting or denying access. SAPL decisions can include constraints, which are additional actions the PEP must perform to grant access. If a constraint is optional, it is called an *advice*. If the constraint is mandatory, it is called an *obligation*.

- *Obligation*, i.e., a mandatory condition that the PEP must fulfil. If this is not possible, access must be denied.
- *Advice*, i.e., an optional condition that the PEP should fulfil. If it fails to do so, access is still granted if the original  decision was `permit`.
- *Transformation* is a special case of an obligation that expresses that the PEP must replace the accessed resource with the resource object supplied in the authorization decision.

```python
policy "filter content in collection"
permit action == "list books"
obligation
	{
		"type" : "jsonContentFilterPredicate",
		"conditions" : [
	                   {
			       						"path" : "$.ageRating",
                       "type" : "<=",
                       "value" : timeBetween(subject.birthday, dateOf(|<now>), "years")
                     }
                  ]
	}
```

#### Handling Constraints

Upon receiving a decision from the PDP containing a constraint, the PEP will check all registered `ConstraintHandler` beans and ask them if they can handle a given constraint defined by the policy. Generally, there is no specific scheme for constraints. Any JSON object may be an appropriate constraint. Its contents solely depend on the domain modelling decisions of the application and policy author. Therefore `ConstraintHandler` must implement the `isResponsible` method of `Responsible` and filter the constraints accordingly.

```java
@Override
public boolean isResponsible(JsonNode constraint) {
	return constraint != null && constraint.has("type") && 					 "filterBooksByAge".equals(constraint.findValue("type").asText()) && constraint.has("age") && constraint.get("age").isInt();
}
```

The SAPL Spring integration offers different hooks in the execution path where applications can add constraint handlers. Depending  on the annotation and if the underlying method returns a value  synchronously or uses reactive datatypes like `Flux<>` or `Mono<>`. 

For each of these hooks, the constraint handlers can influence the execution differently. E.g., for `@PreEnforce`  the constraint handler may attempt to change the arguments handed over to the method. The different hooks map to interfaces a service bean can implement to provide the capability of enforcing different types of constraints.

#### Custom Constraint Handlers

The `ConstraintEnforcementService` manages all executable constraint handlers and furnishes constraint handler bundles (such as `BlockingConstraintHandlerBundle` and `ReactiveConstraintHandlerBundle`) to the PEP whenever the PDP issues a new decision. These bundles consolidate all constraint handlers pertinent to a particular decision.

A custom constraint handler can be integrated by implementing the respective constraint handler interface. The spring policy enforcement points automatically discover and register spring components/beans implementing the interface.

Suitable interfaces are provided for each specific use case.

- The `FilterPredicateConstraintHandlerProvider` enables the modifcation of Predicates based on the prevailing constraints. The default constraint handler providers offer a reference implemention.
- The `RunnableConstraintHandlerProvider` enables the implementation of an action upon the execution of a Runnable. Among other potential applications, this feature is particularly useful for tasks such as logging access to specific resources.
- The `ErrorHandlerProvider` provides a hook into Throwables that may be thrown at runtime, allowing for logging or modifcation of attributes of the Throwable.
- The `MethodInvocationConstraintHandlerProvider` allows the integration of supplementary behavior into method invocations. This includes the modifcation, addition, and removal of method arguments based on the prevailing constraints.
- The `ConsumerConstraintHandlerProvider` enables the implementation of an action upon the consumption of a Consumer of a given type.
- The `SubscriptionHandlerProvider` operates similar to the `ConsumerConstraintHandlerProvider` but specifically where the consumed type is a Subscription. It provides a hook into the subscription to a reactive stream.
- The `RequestHandlerProvider` enables the implementation of an action based on the decision of a PDP, specifically on the AuthorizationDecision.

Two handlers are implemented by default.
- ContentFilteringProvider, enables selective filtering of specific properties within an object
- ContentFilterPredicateProvider, enables selective filtering of specific conditions within a Predicate
