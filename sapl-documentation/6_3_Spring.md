---
layout: default
title: Spring
parent: SDKs and APIs
nav_order: 603
---

## Spring SDK

This library integrates SAPL authorization into Spring Boot applications. You write authorization rules as external policy files, and SAPL enforces them at runtime without code changes or redeployment. For background on why and when to use policy-based authorization, see [Why SAPL?](../1_1_WhySAPL/).

The flow is straightforward. Your application sends an authorization subscription to the Policy Decision Point (PDP). The PDP evaluates its policies and returns a decision. If the decision carries constraints (obligations or advice), constraint handlers execute the appropriate logic before the result reaches the caller. Working examples covering common scenarios are at [sapl-demos](https://github.com/heutelbeck/sapl-demos).

## Quick Start

This walkthrough shows how the pieces fit together end to end.

**1. Add the BOM and snapshot repository to your `pom.xml`.**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-bom</artifactId>
            <version>4.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<repositories>
    <repository>
        <id>central-portal-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>
```

**2. Add the starter dependency.**

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-boot-starter</artifactId>
</dependency>
```

**3. Configure the embedded PDP** in `application.properties`.

```properties
io.sapl.pdp.embedded.enabled=true
io.sapl.pdp.embedded.pdp-config-type=RESOURCES
io.sapl.pdp.embedded.policies-path=/policies
```

This tells SAPL to run a PDP inside your application and load policies from `src/main/resources/policies/`.

**4. Enable SAPL method security.**

```java
@Configuration
@EnableWebSecurity
@EnableSaplMethodSecurity  // for blocking applications
// or @EnableReactiveSaplMethodSecurity for WebFlux
public class SecurityConfig {
}
```

**5. Annotate a method.**

```java
@PreEnforce(subject = "authentication.name", action = "'read'", resource = "#id")
public Book findById(Long id) {
    return bookRepository.findById(id);
}
```

**6. Write a policy** in `src/main/resources/policies/books.sapl`.

```
policy "users can read their own books"
permit
  action == "read";
  subject == resource.ownerId;
```

When someone calls `findById(42)`, SAPL checks whether the authenticated user owns book 42. If yes, the method runs. If no, an `AccessDeniedException` is thrown.

That is the basic pattern. The annotation tells SAPL what to check. The policy decides the outcome.

## Method Security

Method security is where most applications start with SAPL. You annotate methods, and SAPL intercepts the calls to enforce policies. This assumes you have `spring-boot-starter-web` (for servlet) or `spring-boot-starter-webflux` (for reactive) in your dependencies.

### Blocking Applications

For servlet-based Spring Web applications, enable method security and use `@PreEnforce` or `@PostEnforce`.

```java
@Configuration
@EnableSaplMethodSecurity
public class SecurityConfig {
}
```

**`@PreEnforce`** checks authorization before the method runs.

```java
@PreEnforce
public void deleteBook(Long id) {
    bookRepository.deleteById(id);
}
```

If the PDP does not return `PERMIT`, the method never executes.

**`@PostEnforce`** checks authorization after the method runs, with access to the return value.

```java
@PostEnforce(resource = "returnObject")
public Book findById(Long id) {
    return bookRepository.findById(id);
}
```

This is useful when the decision depends on the returned data, or when you want the policy to transform the result. The return object is serialized to JSON for the authorization subscription, so make sure your domain classes are Jackson-serializable. Either follow standard JavaBean conventions, or add Jackson annotations where needed.

### Reactive Applications

For WebFlux applications, use the reactive variant.

```java
@Configuration
@EnableReactiveSaplMethodSecurity
public class SecurityConfig {
}
```

The same `@PreEnforce` and `@PostEnforce` annotations work here. They integrate with the reactive pipeline instead of blocking. One restriction is worth knowing about. `@PostEnforce` on reactive methods only works with `Mono`, not `Flux`. The resource value must be a single object, not a stream. If you need to enforce on a `Flux` return type, apply the policy at a different layer such as filtering inside the publisher, or use `@PreEnforce` together with query-manipulation obligations.

### How Enforcement Works

The annotations are convenient. To use them well, it helps to understand what happens behind the scenes. This section walks through the enforcement lifecycle so you can reason about behavior.

#### The Deny Invariant

One rule governs all enforcement. Only `PERMIT` grants access. The PDP can return four possible decisions (`PERMIT`, `DENY`, `INDETERMINATE`, `NOT_APPLICABLE`). Only `PERMIT` ever results in access being granted. Everything else means denial.

A decision from the PDP looks like this.

```json
{
  "decision": "PERMIT",
  "obligations": [{ "type": "logAccess", "message": "Salary data accessed" }],
  "advice": [{ "type": "notifyAdmin" }]
}
```

The `decision` field is always present. The other fields are optional. The `obligations` and `advice` arrays carry JSON objects, by convention with a `type` field for handler dispatch. When `resource` is present in the decision, it replaces the method's return value entirely.

A `PERMIT` with obligations is not a free pass. The PEP checks that every obligation in the decision has a registered handler. If even one obligation cannot be fulfilled, the PEP treats the decision as a denial. If a handler accepts responsibility for an obligation but fails during execution, that also results in denial. Advice is softer. The PEP tries to execute advice handlers too. If one fails, it logs the failure and moves on. Advice never causes denial.

| Aspect | Obligation | Advice |
|---|---|---|
| All handled? | Required. Unhandled obligations deny access (`AccessDeniedException`). | Optional. Unhandled advice is silently ignored. |
| Handler failure | Denies access (`AccessDeniedException`). | Logs a warning and continues. |

This means you can always trust that if your method runs, every obligation attached to the decision has been successfully enforced.

#### Enforcement Locations

Enforcement does not happen at a single checkpoint. Constraint handlers can intervene at different points in the request lifecycle. SAPL models each point as a distinct *signal*. A handler attaches to a particular signal type, and the PEP fires that signal at the matching lifecycle point.

For request-response methods, the relevant signals are the following.

| Signal | Fires when | Typical handler |
|---|---|---|
| `DecisionSignal` | Authorization decision arrives | Logging, audit, notification. |
| `InputSignal` | Before the method runs (with arguments) | Argument inspection or transformation in `@PreEnforce`. |
| `OutputSignal<T>` | After the method returns (with return value) | Transform, filter, or replace the result. |
| `ErrorSignal` | Method throws | Transform or observe the error. |

There are additional signals for reactive lifecycle events (`SubscriptionSignal`, `CancelSignal`, `CompleteSignal`, `TerminationSignal`, `AfterTerminationSignal`). They behave the same way. A handler attaches to a signal, the PEP fires it at the right moment.

#### `@PreEnforce` Lifecycle

When you annotate a method with `@PreEnforce`, here is the sequence.

The PEP builds an authorization subscription from the SpEL expressions in the annotation (or from defaults if you left them out) and sends it to the PDP as a one-shot request. The PDP evaluates the subscription against all matching policies and returns a single decision.

If the decision is anything other than `PERMIT`, the PEP throws an `AccessDeniedException` immediately. Your method never runs.

If the decision is `PERMIT`, the PEP resolves all constraint handlers. It walks through the obligations and advice attached to the decision and checks which registered handlers claim responsibility for each one. If any obligation has no matching handler, the PEP denies access right there, because it cannot guarantee the obligation will be enforced.

With handlers resolved, execution proceeds through the signals in order. `DecisionSignal` handlers run first (logging, audit). Then `InputSignal` handlers run, which can transform method arguments if the policy requires it. Then your actual method executes. After the method returns, `OutputSignal` handlers apply (resource replacement if the decision included one, mapping handlers, consumer handlers). If any obligation handler fails at any stage, the PEP throws `AccessDeniedException`.

One important consequence is worth calling out. If your method performs a database write and an obligation handler fails after the method has returned, the PEP throws `AccessDeniedException`. With the automatic transaction ordering described in [Transaction Integration](#transaction-integration) below, this exception propagates through the `TransactionInterceptor` and triggers a rollback. The database write does not persist.

#### `@PostEnforce` Lifecycle

`@PostEnforce` inverts the order. Your method runs first, regardless of the authorization outcome. Only after it returns does the PEP build the authorization subscription (now including `returnObject` as a SpEL variable) and consult the PDP.

This means the PDP can make decisions based on the actual data your method produced. For example, a policy might permit access to a document only if the document's classification level is below a threshold. That is something you can only check after loading the document.

If the decision is not `PERMIT`, the PEP discards the return value and throws `AccessDeniedException`. The method ran and its side effects happened. If the method modified a database, the transaction ordering described below ensures a rollback.

If the decision is `PERMIT`, constraint handlers proceed through the same stages as `@PreEnforce`, minus the `InputSignal` handlers (since the method has already run). `OutputSignal` handlers can still transform the result before it reaches the caller.

There is one subtlety worth keeping in mind. Because the method runs before the PDP is consulted, if the method itself throws an exception, that exception propagates directly. The PDP is never called. There is no return value to include in the subscription, and no point in authorizing a failed operation.

For the formal specification of these enforcement modes, including state machines, teardown invariants, and edge cases around handler resolution timing, see the [PEP Implementation Specification](../8_1_PEPImplementationSpecification/).

### Building the Authorization Subscription

Every authorization check sends a subscription to the PDP with four components.

- **subject** Who is making the request.
- **action** What they are trying to do.
- **resource** What they are trying to access.
- **environment** Contextual information such as time or IP address.

By default SAPL collects everything it can find, which creates verbose subscriptions. In practice you will want to be explicit.

```java
@PreEnforce(
    subject  = "authentication.principal",
    action   = "'delete'",
    resource = "#book"
)
public void deleteBook(Book book) { ... }
```

The values are Spring Expression Language (SpEL) expressions. The evaluation context exposes a few useful root variables.

- `authentication` The current Spring Security `Authentication`.
- `#paramName` Method parameters by name (such as `#orderId`).
- `@beanName` Spring beans (such as `@userService.checkAccess()`).
- `methodInvocation` The method invocation itself, including its method and arguments.
- `returnObject` The method's return value (only available in `@PostEnforce`).

A few patterns you will see often.

```java
// Use the username as subject
subject = "authentication.name"

// Use a literal string as action
action = "'create-report'"

// Use a method parameter as resource
resource = "#orderId"

// Call a bean method
subject = "@userService.getCurrentUserProfile()"

// Build a custom object inline
resource = "{ 'type': 'book', 'id': #id }"
```

### Combining `@PreEnforce` and `@PostEnforce`

You can use both annotations on the same method. Both must permit for the result to reach the caller.

```java
@PreEnforce(action = "'read'")
@PostEnforce(resource = "returnObject")
public Document getDocument(Long id) { ... }
```

You cannot mix SAPL annotations with Spring Security annotations like `@PreAuthorize` on the same method. Choose one authorization mechanism per method.

### Transaction Integration

When a `@PreEnforce` or `@PostEnforce` method is also `@Transactional`, an obligation handler failure must trigger a transaction rollback. Consider this service method.

```java
@Transactional
@PreEnforce
public Order createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request));
}
```

If the PDP returns `PERMIT` with an obligation, and the obligation handler fails after the method has successfully saved the order, the correct behavior is to roll back the database transaction. The order should not persist if the obligation cannot be fulfilled.

#### Automatic AOP Order Adjustment

When you enable SAPL method security via `@EnableSaplMethodSecurity` or `@EnableReactiveSaplMethodSecurity`, the transaction interceptor order is automatically adjusted so that the transaction boundary wraps the SAPL enforcement interceptors. No manual configuration is required.

This places the interceptors in the correct order from outermost to innermost.

1. Spring Security `@PreAuthorize` (order 500). Fast deny, no transaction started.
2. `TransactionInterceptor` (order `Integer.MAX_VALUE - 3`). Begins the transaction.
3. SAPL `@PreEnforce` (order `Integer.MAX_VALUE - 1`).
4. SAPL `@PostEnforce` (order `Integer.MAX_VALUE`). Innermost.
5. The actual method executes.

When a SAPL obligation handler throws after the method returns, the exception propagates outward through the `TransactionInterceptor`, which rolls back the transaction.

The automatic adjustment only applies when the transaction advisor still has Spring's default order. If you have explicitly configured a custom order via `@EnableTransactionManagement(order = ...)`, your setting is preserved.

For reactive methods returning `Mono`, the constraint handlers are wired into the reactive pipeline. The `ReactiveTransactionManager` sees the error signal within the pipeline and rolls back automatically, independent of AOP interceptor ordering.

#### Disabling Automatic Adjustment

If the automatic reordering conflicts with your specific AOP interceptor ordering requirements, you can disable it.

```properties
io.sapl.method-security.adjust-transaction-order=false
```

With this property set, the transaction interceptor keeps its default order. Be aware that in blocking scenarios, this means obligation handler failures after a successful method call will not trigger a rollback. The database might be left in an inconsistent state.

## HTTP Request Security

Beyond method security, you can apply SAPL to the HTTP layer. This protects endpoints based on request attributes before any controller code runs and lets policy obligations shape the request that reaches the controller, the response that goes back to the client, and the deny page rendered when access is refused.

### Servlet Wiring

Apply SAPL to `HttpSecurity` through the dedicated configurer the starter ships. One call wires the authorization manager, the HTTP PEP filter, and the access-denied handler.

```java
import static io.sapl.spring.pep.http.servlet.SaplHttpSecurityConfigurer.saplHttp;
import static org.springframework.security.config.Customizer.withDefaults;

@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.with(saplHttp(), withDefaults())
               .formLogin(withDefaults())
               .httpBasic(withDefaults())
               .build();
}
```

`saplHttp()` is `io.sapl.spring.pep.http.servlet.SaplHttpSecurityConfigurer.saplHttp()`. The configurer pulls `SaplAuthorizationManager`, `SaplAccessDeniedHandler`, and `SaplHttpPepFilter` from the application context. All three are deployed by `AuthorizationManagerConfiguration` as `@ConditionalOnMissingBean` beans, so applications can override any of them by declaring their own bean of the same type.

### Reactive Wiring

Reactive applications use the dedicated reactive configurer, applied
explicitly to `ServerHttpSecurity`.

```java
import io.sapl.spring.pep.http.reactive.SaplServerHttpSecurityConfigurer;
import static org.springframework.security.config.Customizer.withDefaults;

@Bean
SecurityWebFilterChain filterChain(ServerHttpSecurity http, ApplicationContext context) {
    SaplServerHttpSecurityConfigurer.apply(http, context);
    return http.formLogin(withDefaults()).httpBasic(withDefaults()).build();
}
```

`SaplServerHttpSecurityConfigurer.apply(http, context)` pulls
`ReactiveSaplAuthorizationManager`, `SaplServerAccessDeniedHandler`, and
`SaplHttpPepWebFilter` from the application context. All three are deployed
by `AuthorizationManagerConfiguration` as `@ConditionalOnMissingBean` beans,
so applications can override any of them by declaring their own bean of the
same type.

The reactive backend fires the same five signals as the servlet backend
(documented below) and the request serializer exposes the same field names
on both stacks: `resource.requestedURI` is the request path,
`resource.contextPath` is the application's deployment context (usually
empty for root deployments). The same policy works against both backends.

### Customizing the Authorization Subscription

By default both authorization managers serialize the inbound request and place it on `action` and `resource`, with the resolved `Authentication` on `subject` and `environment` left undefined. That default is verbose. Most applications eventually want a tighter shape that lines up with what their policies actually reference.

The shape is owned by an `AuthorizationSubscriptionFactory` (servlet) or `ReactiveAuthorizationSubscriptionFactory` (reactive) bean. The starter registers a default factory under `@ConditionalOnMissingBean`. Three override paths are available, in increasing order of locality.

Replace the global factory bean. A single `@Bean` of `AuthorizationSubscriptionFactory` (or its reactive sibling) replaces the default everywhere.

```java
@Bean
AuthorizationSubscriptionFactory subscriptionFactory(ObjectMapper mapper) {
    return (auth, request) -> AuthorizationSubscription.of(
            auth.getName(),
            request.getMethod(),
            Map.of("path", request.getRequestURI(), "tenant", request.getHeader("X-Tenant")),
            mapper);
}
```

Override per filter chain via the configurer. The customizer parameter of `http.with(saplHttp(), ...)` carries the same fluent setter. This is the right place when one chain wants a different subscription shape from another.

```java
http.with(saplHttp(), c -> c.subscriptionFactory(
        (auth, req) -> AuthorizationSubscription.of(auth.getName(),
                req.getMethod(), req.getRequestURI(), mapper)));
```

The reactive form is the same idea, with the second `apply(...)` overload taking the customizer:

```java
SaplServerHttpSecurityConfigurer.apply(http, context, c -> c.subscriptionFactory(
        (auth, exchange) -> Mono.just(AuthorizationSubscription.of(auth.getName(),
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getURI().getPath(), mapper))));
```

The reactive factory returns `Mono<AuthorizationSubscription>` so it can enrich the subscription asynchronously (for example resolving subject attributes from a reactive store) without blocking the event loop. Synchronous customizations stay one-line via `Mono.just(...)`.

Replace the manager outright. When you need behaviour beyond shaping the subscription (for example pre-authorization caching), construct your own `SaplAuthorizationManager` (or its reactive sibling) and pass it through `c.authorizationManager(...)`. The configurer then skips the factory lookup entirely.

### What the HTTP PEP Fires

Five signals reach the planner over the course of a single HTTP exchange. Constraint handlers attach to whichever fits the work they do.

| Signal                       | Fires from                                                                                | Carrier                | Typical handler                                                                 |
|------------------------------|-------------------------------------------------------------------------------------------|------------------------|---------------------------------------------------------------------------------|
| `DecisionSignal`             | The authorization manager (servlet or reactive)                                           | `AuthorizationDecision` | Audit logging, metrics, decision-tagged side effects.                          |
| `HttpRequestSignal`          | The authorization manager                                                                 | `HttpRequest`          | Read-only request observation (audit, structured access logs, rate limiting).   |
| `HttpRequestMutationSignal`  | The HTTP PEP filter pre-chain (`SaplHttpPepFilter` / `SaplHttpPepWebFilter`)              | `MutableHttpRequest`   | Inject headers or attributes that downstream filters and the controller see.    |
| `HttpResponseSignal`         | The HTTP PEP filter post-chain                                                            | `MutableHttpResponse`  | Read or replace status, headers, and body produced by the controller.           |
| `HttpDenialSignal`           | The access-denied handler (`SaplAccessDeniedHandler` / `SaplServerAccessDeniedHandler`)   | `MutableHttpResponse`  | Shape the deny response for an authenticated denial (status, headers, body, redirect). |

The authorization manager stores the active `EnforcementPlan` on a request or exchange attribute keyed by `HttpEnforcementContext.PLAN_ATTRIBUTE` so the downstream PEP filter and access-denied handler find the same plan and dispatch additional signals against it.

`HttpRequestSignal` carries an `org.springframework.http.HttpRequest` view of the inbound request. Mappers are not admissible at this signal because the manager treats the request as read-only at the authorization point. For request mutation use `HttpRequestMutationSignal`, which fires on the permit path before the controller runs.

`HttpResponseSignal` fires only on the normal-return path. If the chain throws, the buffered response is discarded and the exception propagates so the standard Spring error pipeline can produce its own response. Authenticated denials route through the SAPL access-denied handler and fire `HttpDenialSignal` instead. Anonymous denials route through Spring Security's `AuthenticationEntryPoint` (typically a login redirect or a 401 challenge) and never reach the SAPL deny handler.

### MutableHttpRequest and MutableHttpResponse

`MutableHttpRequest` and `MutableHttpResponse` are SAPL abstractions over the underlying request and response on either backend. Handlers see this interface and write portable code. Servlet implementations live under `io.sapl.spring.pep.http.servlet`, reactive implementations under `io.sapl.spring.pep.http.reactive`; cast to a backend type only when a feature outside the interface is required.

```java
public interface MutableHttpRequest {
    void setHeader(String name, String value);
    void addHeader(String name, String value);
    void removeHeader(String name);
    void setAttribute(String name, Object value);
    HttpRequest snapshot();
    boolean isModified();
}

public interface MutableHttpResponse {
    boolean setStatusCode(HttpStatusCode status);
    boolean setStatusCode(int statusValue);
    HttpStatusCode getStatusCode();
    void setHeader(String name, String value);
    void addHeader(String name, String value);
    void removeHeader(String name);
    HttpHeaders headers();
    String getBody();
    void setBody(String body);
    void writeBody(String contentType, String body);
    boolean isModified();
}
```

`setStatusCode` returns `boolean` to match the reactive `ServerHttpResponse.setStatusCode` contract: `true` when the status was applied, `false` when the response is already committed. Servlet implementations always return `true` since the buffered status is set on a buffer, not on the underlying response. Most callers ignore the return value.

`isModified()` ticks for every typed mutation. The PEP filter uses it to skip forwarding the request wrapper down the chain when the obligation handler observed without changing anything. The access-denied handler uses it together with the plan's denial-signal entry list to decide whether to commit the obligation-shaped response or fall back to Spring's default 403.

### Performance Characteristics

The HTTP PEP filter wraps the request and response only when it has work to do. It checks the active plan for handlers scheduled at `HttpRequestMutationSignal` and `HttpResponseSignal` before installing either wrapper. The common case (a permit decision with no HTTP signal handlers) runs against the raw request and response with no extra copy.

When response-side handlers are scheduled, the filter installs a buffering wrapper that captures every controller byte in memory and re-emits it on commit. This makes body inspection and rewrite possible but is unsuitable for unbounded streaming bodies. Constraint handler authors who need response shaping should be aware of the in-memory capture; routes that intentionally stream large payloads should not register response-signal handlers.

When request-side handlers are scheduled, the filter installs a header-override wrapper, fires the mutation signal, and only forwards the wrapper to the chain when at least one handler actually called a setter. Pure observation handlers cost nothing beyond the signal dispatch.

### Constraint Handlers at the HTTP Layer

Constraint handlers attach to HTTP signals exactly the way they attach to method-security signals. The provider returns a list of `ScopedConstraintHandler` entries scoped to the signal each handler should fire on. See [Writing Custom Handlers](#writing-custom-handlers) below for the general shape.

A short example, an obligation that injects an `X-Tenant` header on the request before the controller runs:

```java
@Component
public class TenantHeaderHandler implements ConstraintHandlerProvider {

    @Override
    public List<ScopedConstraintHandler> getConstraintHandlers(
            Value constraint, Set<SignalType> supportedSignals) {

        if (!ConstraintResponsibility.isResponsible(constraint, "tenant-header")) {
            return List.of();
        }
        if (!supportedSignals.contains(Signal.HttpRequestMutationSignal.TYPE)) {
            return List.of();
        }
        if (!(constraint instanceof ObjectValue obj)
                || !(obj.get("value") instanceof TextValue(String tenant))) {
            return List.of();
        }
        ConstraintHandler.Consumer<MutableHttpRequest> handler =
                request -> request.setHeader("X-Tenant", tenant);
        return List.of(new ScopedConstraintHandler(
                handler, Signal.HttpRequestMutationSignal.TYPE, 0));
    }
}
```

The matching policy:

```sapl
policy "stamp_tenant"
permit
    action.method == "GET";
    resource.requestedURI =~ "/api/.*";
obligation
    { "type": "tenant-header", "value": "demo-tenant" }
```

## Constraints

So far we have talked about permit and deny decisions. SAPL can do more. A decision can include constraints that the PEP must enforce. The obligation and advice contract was covered above in [The Deny Invariant](#the-deny-invariant). This section shows how to write policies with constraints, and how to implement the handlers that enforce them.

A policy with constraints looks like this.

```
policy "permit with logging"
permit
  action == "read-salary";
obligation {
             "type": "logAccess",
             "message": "Salary data accessed"
           }
advice     {
             "type": "notify",
             "channel": "audit"
           }
```

### Built-in Constraint Handlers

SAPL Spring Security ships with handlers for common scenarios.

#### `filterJsonContent`

`ContentFilteringProvider` filters or transforms properties within returned objects. You can blacken (replace with a marker character), delete, or replace specific JSON paths.

```
obligation {
             "type": "filterJsonContent",
             "actions": [
               { "type": "blacken", "path": "$.ssn" },
               { "type": "delete",  "path": "$.salary" }
             ]
           }
```

The full schema looks like this.

```json
{
  "type": "filterJsonContent",
  "conditions": [
    { "path": "$.field", "type": "==", "value": "..." }
  ],
  "actions": [
    { "path": "$.field", "type": "delete" },
    { "path": "$.field", "type": "blacken",
      "replacement": "X", "length": 4, "discloseLeft": 1, "discloseRight": 1 },
    { "path": "$.field", "type": "replace", "replacement": "REDACTED" }
  ]
}
```

The optional `conditions` array narrows which elements the actions apply to. Each condition has a JSONPath, a comparison type (`==`, `!=`, `>=`, `<=`, `>`, `<`, or `=~` for regex), and a value. All conditions must match (AND-joined) for the actions to apply. Action types are `delete` (remove the node), `blacken` (obfuscate text with optional partial disclosure), and `replace` (substitute the value). The provider works on `Optional`, `List`, `Set`, `Mono`, `Flux`, arrays, and single objects.

#### `jsonContentFilterPredicate`

`ContentFilterPredicateProvider` filters elements out of collections based on a predicate. This is useful for age-gating or classification-based filtering.

```
policy "age-rating filter"
permit
  action == "list books";
obligation {
             "type": "jsonContentFilterPredicate",
             "conditions": [
               {
                 "path":  "$.ageRating",
                 "type":  "<=",
                 "value": timeBetween(subject.birthday, dateOf(|<now>), "years")
               }
             ]
           }
```

This example uses SAPL's built-in `timeBetween` and `dateOf` functions to calculate the user's age and filter out books with age ratings above that age. The schema accepts only `conditions`, with the same shape as in `filterJsonContent`. Elements that do not match all conditions are dropped from the collection.

#### Query Manipulation

`SqlQueryManipulationProvider` and `MongoDbQueryManipulationProvider` rewrite database queries to filter at the data layer. They are covered in detail in the [Query Manipulation](#query-manipulation) section below.

### Writing Custom Handlers

When the built-in handlers are not enough, you write your own. A constraint handler is a Spring bean that implements `ConstraintHandlerProvider`.

The interface is small.

```java
public interface ConstraintHandlerProvider {
    List<ScopedConstraintHandler> getConstraintHandlers(
        Value constraint, Set<SignalType> supportedSignals);
}
```

The PEP calls `getConstraintHandlers` for each constraint in a decision. Your provider inspects the constraint and decides whether it can handle it. If yes, return one or more `ScopedConstraintHandler` entries. Each entry bundles three things together.

- A `ConstraintHandler<T>`, which is the actual logic.
- The `SignalType` it should attach to.
- A priority (lower runs earlier among handlers on the same signal).

If no, return an empty list, and the PEP will ask other providers. If no provider claims a constraint that arrived as an obligation, the PEP denies access. If more than one provider claims the same constraint, the planner treats that as ambiguous and denies access.

A single obligation can drive several handlers across different lifecycle points. For example, an `auditAndStamp` obligation can return both a `DecisionSignal` runner that records the decision and an `HttpResponseSignal` consumer that adds an audit header to the response. The planner schedules each handler against its own signal independently. The bundle is all-or-nothing during admissibility checks. If any handler in the returned list is not well-formed (for example a mapper attached to a signal the calling PEP does not advertise), the entire claim is rejected.

There are three handler shapes, all under the sealed `ConstraintHandler<T>` interface.

| Shape | Signature | Use when |
|---|---|---|
| `Mapper<T>` | `T apply(T)` | You need to transform the value flowing through a value signal. Examples include redacting fields in `OutputSignal` or rewriting a SQL string in `SqlShimSignal`. |
| `Consumer<T>` | `void accept(T)` | You need a side effect that has access to the value but does not change it. Example: structured audit logging that records the return value. |
| `Runner` | `void run()` | You need a side effect that does not need a value. Examples include logging the decision or sending a notification. |

One subtle rule is worth knowing before you hit it. A `Mapper` may only be returned for an obligation, never for advice. If a constraint arrived as advice and your provider returns a `Mapper`, the planner replaces it with a synthetic failure runner during planning. The reasoning is that advice is allowed to fail silently. A value transformation that silently does not happen would leave the caller unable to tell whether the result was transformed or not, which is an unsafe contract. If you want a transformation to apply, the policy must mark the constraint as an obligation. `Consumer` and `Runner` handlers can be returned for either obligation or advice.

Here is a complete example that logs access attempts on every decision.

```java
@Component
public class LogAccessHandler implements ConstraintHandlerProvider {

    private static final Logger log = LoggerFactory.getLogger(LogAccessHandler.class);
    private static final String CONSTRAINT_TYPE = "logAccess";

    @Override
    public List<ScopedConstraintHandler> getConstraintHandlers(
            Value constraint, Set<SignalType> supportedSignals) {

        if (!ConstraintResponsibility.isResponsible(constraint, CONSTRAINT_TYPE)) {
            return List.of();
        }

        var message = extractMessage(constraint);
        ConstraintHandler.Runner handler = () -> log.info(message);

        return List.of(new ScopedConstraintHandler(handler, DecisionSignal.TYPE, 0));
    }

    private static String extractMessage(Value constraint) {
        if (constraint instanceof ObjectValue obj
                && obj.get("message") instanceof TextValue(String text)) {
            return text;
        }
        return "Access logged";
    }
}
```

Two things are worth pointing out.

First, the responsibility check uses the helper `ConstraintResponsibility.isResponsible(constraint, type)`, which checks whether the constraint is a JSON object with a `type` field matching the given string. This is the convention used by all built-in providers. You are free to use a different convention if it makes more sense for your obligations.

Second, the handler attaches to `DecisionSignal.TYPE`. The PEP fires `DecisionSignal` once when the decision arrives, before the method runs. If you want to log on completion instead, attach to `CompleteSignal.TYPE`. If you want to inspect the return value, attach to `OutputSignal.typeFor(SomeReturnType.class)` and use a `Consumer<SomeReturnType>` handler.

Spring auto-discovers any bean implementing `ConstraintHandlerProvider`. Just annotate with `@Component` and put it in a scanned package.

## Query Manipulation

Spring Data applications often want to filter results at the database, not in memory. SAPL supports this with two backends today, R2DBC and reactive MongoDB. The mechanism is a transparent shim. You do not annotate repository methods. You apply `@PreEnforce` on the calling service method as usual, and the policy emits a query manipulation obligation. The shim catches the query as Spring Data dispatches it, applies the obligation, and sends the rewritten query to the driver.

### How the Shim Works

When the SAPL Spring Boot starter sees `R2dbcRepository` or `ReactiveMongoTemplate` on the classpath, it activates an auto-configuration that wraps two beans.

- `DatabaseClient` for R2DBC. Every R2DBC dispatch path bottoms out at `DatabaseClient.sql(...)`. The shim wraps that call.
- `ReactiveMongoTemplate` for MongoDB. The shim intercepts both the legacy entry points (`find`, `findOne`, `exists`, `count`, `remove`) and the fluent `query(Class).matching(Query)` chain that derived queries use internally.

When a service method annotated with `@PreEnforce` triggers a decision carrying a query manipulation obligation, the obligation is bound to the active enforcement plan. Calls that flow through your repository while that plan is active reach the shim, fire a shim signal, apply the rewritten query, and forward to the driver.

When no plan is active (for example, when the same repository is called from a controller without `@PreEnforce`), the shim passes the query through unchanged. There is no global filter. The obligation only applies inside the protected service call.

The obligation can never widen the user's filter. It can only narrow it. If the user requested rows where `category = 'art'` and the obligation says `tenant_id = 7`, the resulting query asks for rows where both conditions hold.

### SQL: `sql:queryManipulation`

For R2DBC and other SQL backends, the constraint type is `sql:queryManipulation` (the alias `relational:queryManipulation` is accepted as a synonym). The provider supports a typed criteria language for portable obligations and a string escape hatch for backend-specific SQL.

A simple obligation that adds a tenant filter and projects only some columns.

```
obligation {
             "type": "sql:queryManipulation",
             "criteria": [
               { "column": "tenant_id", "op": "=", "value": 7 }
             ],
             "columns": [ "id", "title", "author" ]
           }
```

The full schema.

```jsonc
{
  "type":       "sql:queryManipulation",
  "criteria":   [],   // typed criteria, AND-joined at top level
  "conditions": [],   // raw SQL fragments, AND-joined
  "columns":    []    // SELECT projection narrowing
}
```

A typed criterion is a JSON object with `column`, `op`, and `value`.

```json
{ "column": "status", "op": "=", "value": "active" }
```

The supported operators are `=`, `!=`, `>`, `>=`, `<`, `<=`, `in` (with an array `value`), `like`, `notLike`, `isNull`, and `isNotNull`. The `isNull` and `isNotNull` operators do not need a `value`.

You can group criteria with `or` and `and`, and groups can be nested.

```json
[
  { "column": "tenant_id", "op": "=", "value": 7 },
  { "or": [
    { "column": "owner_id",  "op": "=", "value": "alice" },
    { "column": "is_public", "op": "=", "value": true }
  ]}
]
```

Each top-level entry in the `criteria` array is AND-joined with the others.

The `conditions` array carries raw SQL fragments. Use this for SQL features the typed language does not cover, such as `BETWEEN`, `EXISTS`, or vendor functions.

```json
{ "conditions": [ "created_at > CURRENT_TIMESTAMP - INTERVAL '7 days'" ] }
```

The `columns` array narrows the SELECT projection. If the original query is `SELECT *`, the obligation columns become the projection. If the original query already projects specific columns, the obligation columns intersect with them. The `columns` array applies only to SELECT statements. For UPDATE and DELETE it is ignored.

### MongoDB: `mongo:queryManipulation`

For reactive MongoDB, the constraint type is `mongo:queryManipulation`.

```
obligation {
             "type": "mongo:queryManipulation",
             "criteria": [
               { "column": "tenantId", "op": "=", "value": 7 }
             ]
           }
```

The schema mirrors the SQL provider, minus the `columns` projection feature.

```jsonc
{
  "type":       "mongo:queryManipulation",
  "criteria":   [],   // typed criteria, AND-joined at top level
  "conditions": []    // raw BSON fragments, AND-joined
}
```

The typed criteria language accepts the same operators as SQL except `like` and `notLike`. For pattern matching use the `conditions` escape hatch with `$regex`.

```json
{ "conditions": [ "{ 'name': { '$regex': '^A' } }" ] }
```

Conditions use the standard MongoDB BSON query syntax. The provider parses each fragment and intersects it with the user's query inside a top-level `$and` array. The original query is preserved. The obligation can never overwrite a field the user is already filtering on.

### Worked Example

A service method that lists books for the current user.

```java
@Service
public class LibraryService {

    private final BookRepository books;

    LibraryService(BookRepository books) {
        this.books = books;
    }

    @PreEnforce(subject = "authentication.name", action = "'list-books'")
    public Flux<Book> listBooks() {
        return books.findAll();
    }
}
```

A policy that restricts each user to books belonging to their tenant.

```
policy "books are tenant-scoped"
permit
  action == "list-books";
obligation {
             "type": "sql:queryManipulation",
             "criteria": [
               { "column": "tenant_id", "op": "=", "value": subject.tenantId }
             ]
           }
```

When a user from tenant 7 calls `listBooks()`, the SQL the database executes carries an additional `WHERE tenant_id = 7`. The user only ever sees their own tenant's books. No code in `LibraryService` or `BookRepository` had to change.

The same pattern works for derived queries (`findByAuthor`, `findByPriceLessThan`), `@Query`-annotated methods, and direct calls to `databaseClient.sql(...)`. Every R2DBC dispatch path eventually reaches the shim.

### Disabling the Shim per Engine

You may want to keep the SAPL starter in your application without letting it wrap your data access beans. Common cases include integration tests against a fixture database, a phased rollout where you have not yet authored query manipulation policies, or wanting to enforce only at the method-call boundary.

Each shim has its own opt-out property, both default `true`.

```properties
# Disable the R2DBC shim
io.sapl.method-security.r2dbc-shim.enabled=false

# Disable the Mongo shim
io.sapl.method-security.mongo-shim.enabled=false
```

Setting either to `false` removes that engine's auto-configuration. The corresponding `BeanPostProcessor` does not register, your `DatabaseClient` and `ReactiveMongoTemplate` beans are not wrapped, and any `sql:queryManipulation` or `mongo:queryManipulation` obligation on a decision becomes an unhandled obligation, which the PEP treats as a denial. Keep this in mind when you disable a shim. If your policies still emit the obligation type, requests will start failing closed.

## Configuration

SAPL Spring Security is configured through `application.properties` or `application.yml`. The properties control which PDP to use and how it behaves, plus a few cross-cutting toggles for method security, JWT injection, and query manipulation.

### Embedded PDP

The embedded PDP runs inside your application. Policies are loaded from bundled resources or a filesystem directory.

```properties
io.sapl.pdp.embedded.enabled=true
io.sapl.pdp.embedded.pdp-config-type=RESOURCES
io.sapl.pdp.embedded.policies-path=/policies
io.sapl.pdp.embedded.config-path=/policies
```

The full property list.

| Property | Default | Description |
|---|---|---|
| `io.sapl.pdp.embedded.enabled` | `true` | Enable or disable the embedded PDP. |
| `io.sapl.pdp.embedded.pdp-config-type` | `RESOURCES` | Source of policies and configuration. See [PDP Data Sources](#pdp-data-sources) below. |
| `io.sapl.pdp.embedded.policies-path` | `/policies` | Directory containing `.sapl` policy files. |
| `io.sapl.pdp.embedded.config-path` | `/policies` | Directory containing the `pdp.json` configuration file. |
| `io.sapl.pdp.embedded.function-cache-size` | `10000` | Maximum number of cached pure-function results. SAPL functions are side-effect-free, so the PDP caches results across evaluations using a Window-TinyLFU policy. Set to `0` to disable caching. |
| `io.sapl.pdp.embedded.metrics-enabled` | `false` | Record PDP decision metrics for Prometheus through Micrometer. |
| `io.sapl.pdp.embedded.print-trace` | `false` | Log the full JSON evaluation trace on each decision. Verbose, for debugging. |
| `io.sapl.pdp.embedded.print-json-report` | `false` | Log the JSON decision report on each decision. |
| `io.sapl.pdp.embedded.print-text-report` | `false` | Log a human-readable decision report on each decision. |
| `io.sapl.pdp.embedded.print-subscription-events` | `false` | Log new authorization subscriptions. |
| `io.sapl.pdp.embedded.print-unsubscription-events` | `false` | Log ended authorization subscriptions. |
| `io.sapl.pdp.embedded.pretty-print-reports` | `false` | Pretty-print JSON in logged traces and reports. |

#### PDP Data Sources

The `pdp-config-type` property selects where policies come from.

| Value | Behavior |
|---|---|
| `RESOURCES` | Loads from the classpath. Bundled in your JAR, fixed at build time. Convenient for development. |
| `DIRECTORY` | Loads from a filesystem directory and watches for changes. Updates apply to live subscriptions. |
| `MULTI_DIRECTORY` | Loads multiple subdirectories from a base directory. Each subdirectory name becomes a `pdpId` for multi-tenant routing. |
| `BUNDLES` | Loads `.saplbundle` files from a directory. Each bundle filename (without extension) becomes a `pdpId`. |
| `REMOTE_BUNDLES` | Fetches `.saplbundle` files from a remote HTTP server. Supports polling and long-poll change detection. |

For development, `RESOURCES` is convenient because policies travel with the JAR. For production with dynamic policy updates, use `DIRECTORY` and point to a directory that can be updated without redeployment. For multi-tenant deployments, the `MULTI_DIRECTORY`, `BUNDLES`, and `REMOTE_BUNDLES` source types create one `pdpId` per subdirectory or bundle.

#### Bundle Security

When using `BUNDLES` or `REMOTE_BUNDLES`, you can configure signature verification so tampered bundles are rejected at load time. The defaults are conservative. If you set a public key, all bundles must be signed and verify against that key. If you do not set a key, you must explicitly enable unsigned acceptance with `allow-unsigned=true`. Otherwise startup fails.

| Property | Default | Description |
|---|---|---|
| `io.sapl.pdp.embedded.bundle-security.public-key-path` | none | Path to an Ed25519 public key file. |
| `io.sapl.pdp.embedded.bundle-security.public-key` | none | Base64-encoded Ed25519 public key. Alternative to `public-key-path` for containerized deployments. |
| `io.sapl.pdp.embedded.bundle-security.allow-unsigned` | `false` | Accept unsigned bundles. Use only in development. |
| `io.sapl.pdp.embedded.bundle-security.unsigned-tenants` | empty | List of tenant identifiers that may load unsigned bundles without the global `allow-unsigned` flag. |
| `io.sapl.pdp.embedded.bundle-security.keys.<key-id>` | empty map | Named key catalogue mapping key identifiers to Base64-encoded Ed25519 public keys. |
| `io.sapl.pdp.embedded.bundle-security.tenants.<pdpId>` | empty map | Per-tenant key binding. Maps a tenant identifier to a list of trusted key identifiers from the catalogue. |

#### Remote Bundle Fetching

When `pdp-config-type=REMOTE_BUNDLES`, bundles are fetched from a remote HTTP server. Change detection uses HTTP conditional requests (ETag and `If-None-Match`).

| Property | Default | Description |
|---|---|---|
| `io.sapl.pdp.embedded.remote-bundles.base-url` | none | Base URL of the bundle server. Bundles are fetched as `{base-url}/{pdpId}`. |
| `io.sapl.pdp.embedded.remote-bundles.pdp-ids` | empty | List of PDP identifiers to fetch bundles for. |
| `io.sapl.pdp.embedded.remote-bundles.mode` | `POLLING` | `POLLING` for interval-based or `LONG_POLL` for long-poll change detection. |
| `io.sapl.pdp.embedded.remote-bundles.poll-interval` | `30s` | Default polling interval. |
| `io.sapl.pdp.embedded.remote-bundles.long-poll-timeout` | `30s` | Server hold timeout for long-poll mode. |
| `io.sapl.pdp.embedded.remote-bundles.auth-header-name` | none | HTTP header name for authentication (such as `Authorization`). |
| `io.sapl.pdp.embedded.remote-bundles.auth-header-value` | none | HTTP header value for authentication (such as `Bearer <token>`). |
| `io.sapl.pdp.embedded.remote-bundles.follow-redirects` | `true` | Follow HTTP 3xx redirects. |
| `io.sapl.pdp.embedded.remote-bundles.pdp-id-poll-intervals.<id>` | empty | Per-`pdpId` poll interval overrides. |
| `io.sapl.pdp.embedded.remote-bundles.first-backoff` | `500ms` | Initial backoff after a fetch failure. |
| `io.sapl.pdp.embedded.remote-bundles.max-backoff` | `5s` | Maximum backoff after repeated failures. |

### Remote PDP

The remote PDP connects to an external PDP server (such as SAPL Node). Use this when policies are managed centrally or when multiple applications share the same policies.

```properties
io.sapl.pdp.remote.enabled=true
io.sapl.pdp.remote.type=http
io.sapl.pdp.remote.host=https://pdp.example.org:8443

# Basic authentication
io.sapl.pdp.remote.key=myapp
io.sapl.pdp.remote.secret=secret123

# Or API key authentication
io.sapl.pdp.remote.api-key=your-api-key

# Or token relay (forward the caller's bearer token)
io.sapl.pdp.remote.token-relay=true
```

| Property | Default | Description |
|---|---|---|
| `io.sapl.pdp.remote.enabled` | `false` | Enable or disable the remote PDP. |
| `io.sapl.pdp.remote.type` | `http` | Connection type. Only `http` is supported today. |
| `io.sapl.pdp.remote.host` | empty | HTTP URL of the PDP server. |
| `io.sapl.pdp.remote.key` | empty | Username for basic authentication. |
| `io.sapl.pdp.remote.secret` | empty | Password for basic authentication. |
| `io.sapl.pdp.remote.api-key` | empty | API key for token authentication. |
| `io.sapl.pdp.remote.token-relay` | `false` | Forward the caller's JWT on each PDP request. Mutually exclusive with `key`/`secret` and `api-key`. |
| `io.sapl.pdp.remote.ignore-certificates` | `false` | Skip TLS certificate validation. Not for production. |

You must configure exactly one authentication mechanism. Either `key` and `secret` together, or `api-key` alone, or `token-relay` alone. Token relay is useful when each request to the PDP should carry the caller's identity, so the PDP can apply its own user-aware policies.

### Method Security Properties

| Property | Default | Description |
|---|---|---|
| `io.sapl.method-security.adjust-transaction-order` | `true` | Reorder the `TransactionInterceptor` so the transaction wraps SAPL enforcement. Set to `false` if you have explicit AOP order requirements. See [Transaction Integration](#transaction-integration). |
| `io.sapl.method-security.r2dbc-shim.enabled` | `true` | Wrap `DatabaseClient` for R2DBC query manipulation. Set to `false` to disable the shim. See [Disabling the Shim per Engine](#disabling-the-shim-per-engine). |
| `io.sapl.method-security.mongo-shim.enabled` | `true` | Wrap `ReactiveMongoTemplate` for MongoDB query manipulation. Set to `false` to disable the shim. See [Disabling the Shim per Engine](#disabling-the-shim-per-engine). |

### JWT Token Injection

When your application is an OAuth2 resource server using Spring Security's JWT support, SAPL can automatically inject the bearer token into authorization subscription secrets. This allows the JWT PIP to validate tokens and extract claims in policies through `<jwt.token>`.

This is opt-in for a reason. Passing a bearer token across the PEP and PDP boundary is a deliberate security trade-off. The token is placed into `subscriptionSecrets`, which is never exposed to policy evaluation, never appears in logs or `toString()` output, and is only accessible to PIPs through the `AttributeAccessContext`. It does cross a trust boundary, so it requires explicit activation.

```properties
io.sapl.jwt.inject-token=true
io.sapl.jwt.secrets-key=jwt
```

| Property | Default | Description |
|---|---|---|
| `io.sapl.jwt.inject-token` | `false` | Inject the raw encoded JWT from `JwtAuthenticationToken` into subscription secrets. |
| `io.sapl.jwt.secrets-key` | `jwt` | Key name in subscription secrets. Must match the `secretsKey` configured in the JWT PIP section of `pdp.json`. |

The auto-configuration activates only when both conditions are met.

1. `io.sapl.jwt.inject-token=true` is set.
2. `spring-security-oauth2-resource-server` is on the classpath, providing `JwtAuthenticationToken`.

Once enabled, every authorization subscription built from `@PreEnforce` or `@PostEnforce` will automatically include the bearer token in its secrets when the authenticated principal is a `JwtAuthenticationToken`. For other authentication types, no token is injected.

If the annotation also specifies an explicit `secrets` SpEL expression, the SpEL expression takes precedence and the auto-injected token is not used.

Policies can then validate and inspect the token through the JWT PIP.

```
policy "require valid token with admin scope"
permit
    <jwt.token>.valid;
    "admin" in <jwt.token>.payload.scope
```

The corresponding `pdp.json` configures the JWT PIP with public key resolution.

```json
{
  "variables": {
    "jwt": {
      "secretsKey": "jwt",
      "publicKeyServer": {
        "uri": "http://auth-server:9000/public-key/{kid}",
        "method": "GET",
        "keyCachingTtlMillis": 300000
      }
    }
  }
}
```

### Subject Field Stripping

When no explicit `subject` expression is provided in `@PreEnforce` or `@PostEnforce`, SAPL serializes the full `Authentication` object as the subject. To prevent accidental credential leakage, the following fields are automatically stripped from the default subject serialization.

| Field | Description |
|---|---|
| `credentials` | Removed from the root authentication object. |
| `token.tokenValue` | Raw encoded token removed from the token object (such as a JWT bearer token). |
| `principal.password` | Password removed from the principal object. |
| `principal.tokenValue` | Raw encoded token removed from the principal object. |

Stripping applies only to the default subject construction. If you provide an explicit `subject` SpEL expression, no stripping occurs. You are responsible for excluding sensitive fields.

## Health Indicator

When Spring Boot Actuator is on the classpath and the embedded PDP is enabled, SAPL automatically registers a health indicator at `/actuator/health`. It reports the operational status of all configured PDP instances.

The mapping from PDP states to overall health.

| PDP State | Health Status | Meaning |
|---|---|---|
| All `LOADED` | `UP` | All PDPs have successfully compiled their policies. |
| Any `STALE` | `UP` (with warning) | A policy reload failed, but the PDP is still serving the previous valid configuration. |
| Any `ERROR` or no PDPs | `DOWN` | A PDP has no valid configuration and is returning `INDETERMINATE` decisions. |

Each PDP's details include the configuration ID, combining algorithm, document count, and timestamps for the last successful and failed loads. This information appears in the health endpoint response under the `sapl` component.

No additional configuration is needed. The health indicator is active whenever `spring-boot-starter-actuator` is a dependency and `io.sapl.pdp.embedded.enabled` is `true` (the default).

## Common Questions

**How does this differ from `@PreAuthorize`?**

Spring's `@PreAuthorize` evaluates a SpEL expression at runtime. The logic lives in your Java code. SAPL evaluates external policy files, so the logic is separate from your code. This matters when policies change frequently, when non-developers need to review rules, or when the same policies apply across multiple applications.

**What is the performance impact?**

Each authorization check calls the PDP. With an embedded PDP, this is an in-memory call, typically sub-millisecond. With a remote PDP, there is network latency. The PDP caches policy evaluation, so repeated similar requests are fast. For most applications, the overhead is negligible compared to database or network I/O.

**Can I use SAPL alongside `@PreAuthorize`?**

On different methods, yes. On the same method, no. SAPL annotations and Spring Security annotations cannot be combined on a single method.

**What happens if the PDP is unavailable?**

With an embedded PDP, this is not an issue since it is part of your application. With a remote PDP, you configure the behavior such as deny by default, permit by default, or use cached decisions. The safe default is deny.

**Where do policy files go?**

By default, `src/main/resources/policies/`. The embedded PDP loads from this path when `pdp-config-type=RESOURCES`. If you use `DIRECTORY`, specify an absolute path and the PDP will watch for changes.

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| `AccessDeniedException` despite PERMIT | Unhandled obligation | Check that a constraint handler's responsibility check matches the obligation's `type`. |
| Handler not firing | Missing `@Component` | Ensure the handler class is annotated with `@Component` and lives in a scanned package. |
| All decisions are DENY or INDETERMINATE | PDP unreachable or misconfigured | Verify `io.sapl.pdp.embedded.enabled` or remote PDP connection settings. |
| `ClassCastException` on return-value transformation | Return type not Jackson-serializable | Add Jackson annotations or ensure the class follows JavaBean conventions. |
| `@PostEnforce` not seeing `returnObject` | Method returns void | `@PostEnforce` requires a non-void return value to build the subscription. |
| Obligation handler runs but access still denied | Handler threw an exception | Check logs for handler errors. Any obligation handler failure results in denial. |
| Transaction not rolling back on denial | Custom transaction order | Verify `io.sapl.method-security.adjust-transaction-order` is not disabled. See [Transaction Integration](#transaction-integration). |
| Query manipulation obligation present but query was not rewritten | Shim disabled or driver bean not wrapped | Confirm `io.sapl.method-security.r2dbc-shim.enabled` (or `mongo-shim.enabled`) is `true`. The corresponding starter (`spring-data-r2dbc` or `spring-data-mongodb` reactive) must also be on the classpath for the auto-configuration to register the `BeanPostProcessor`. |

## Next Steps

The best way to learn is to try it. Start with method security on one or two endpoints. Write simple permit and deny policies. Once that works, add an obligation to see how constraints work, then a query manipulation obligation to see how the shim transparently filters at the database layer.

For more details.

- [SAPL Documentation](https://sapl.io/docs) for the policy language reference.
- [sapl-demos](https://github.com/heutelbeck/sapl-demos) for example applications.
