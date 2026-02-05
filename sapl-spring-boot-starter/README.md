# SAPL Spring Security

This library adds attribute-based access control to Spring applications. You write authorization rules as external policy files, and SAPL enforces them at runtime without code changes or redeployment.

## Do You Need This?

Before diving in, let's be honest about when SAPL makes sense and when it doesn't.

### The Limits of Roles and Scopes

Most applications start with role-based access control. Users have roles like `ADMIN`, `MANAGER`, `USER`. Methods check roles with `@PreAuthorize("hasRole('ADMIN')")`. This works until it doesn't.

Consider a document management system. At first, "admins can edit, users can view" is enough. Then requirements arrive: users should edit documents they created. Now you need `@PreAuthorize("hasRole('ADMIN') or #document.createdBy == authentication.name")`. Then: managers can edit documents from their department. The expression grows. Then: during audit periods, nobody can edit financial documents. Now you're checking dates and document categories in SpEL expressions that span multiple lines.

JWT scopes have similar limits. A token might grant `documents:write`, but that says nothing about which documents, under what circumstances, or with what restrictions. The scope is a static capability assigned at login. It can't adapt to context that emerges during the session.

Role-based and scope-based systems answer "what can this user do in general?" SAPL answers "can this specific user do this specific action on this specific resource right now given everything we know?"

### When Attributes Matter

Attribute-based access control (ABAC) makes decisions using attributes of the subject, the resource, the action, and the environment. This handles scenarios that roles cannot express.

**Ownership and delegation.** A user can edit their own profile. A user can view reports they created or that were shared with them. A manager can approve expenses from their direct reports but not from other departments. These rules reference relationships between the user and the resource that don't fit into static roles.

**Temporal constraints.** Trading systems that block transactions outside market hours. HR systems that restrict access to compensation data during review periods. Medical records that allow emergency access but log it for review. Time isn't a role.

**Environmental context.** Allowing access only from corporate networks. Requiring step-up authentication for sensitive operations. Reducing permissions when a risk score exceeds a threshold. These conditions exist outside both the user and the resource.

**Resource classification.** Documents marked confidential require different handling than public documents. The same user might have full access to one and read-only access to another based on metadata, not on who the user is.

### Information Flow Control

Some domains have formal security models that go beyond "can user X access resource Y."

**Bell-LaPadula** enforces "no read up, no write down." A user with SECRET clearance can read SECRET and CONFIDENTIAL documents but not TOP SECRET. They can write to SECRET and TOP SECRET but not CONFIDENTIAL. This prevents information from flowing from high classification to low classification. Implementing this with roles means creating a role for every clearance level and manually encoding the read/write asymmetry. With SAPL, you write the actual rule: permit read if subject.clearance >= resource.classification.

**Brewer-Nash (Chinese Wall)** prevents conflicts of interest. An analyst who has accessed data from Bank A cannot later access data from Bank B if they're competitors. The constraint isn't about who the user is, but about what they've already seen in this session. This requires tracking access history and making dynamic decisions. Roles can't express "permit unless you've previously accessed a conflicting dataset."

**Need-to-know** restricts access to the minimum necessary for a task. A claims processor can see claims assigned to them, not all claims. A developer can access logs for services they maintain, not all services. The "need" comes from the current work context, not from a permanent role assignment.

### Multi-Tenancy

Multi-tenant systems serve multiple customers from shared infrastructure. Each tenant expects isolation, but they also expect customization.

The baseline is data isolation: tenant A never sees tenant B's data. This is often handled at the query level. But tenants also want their own authorization rules. One tenant requires manager approval for orders over $10,000. Another requires dual approval for any external transfer. A third has no approval workflow at all.

Encoding every tenant's rules in your codebase doesn't scale. You'd redeploy for every tenant onboarding and every rule change. With externalized policies, each tenant's rules live in their own policy set. The application code stays constant. Tenant-specific logic stays in tenant-specific configuration.

### Process and Workflow

Authorization often depends on where you are in a business process.

A purchase order in DRAFT state can be edited by the creator. Once SUBMITTED, only the approver can act on it. Once APPROVED, it's read-only except for finance. Once PAID, it's archived and only auditors can access it.

Each state transition changes who can do what. The permissions aren't attached to users or even to the document type. They're attached to the document's current state and the user's role in the workflow.

Modeling this with roles means creating roles like `PO_CREATOR`, `PO_APPROVER`, `PO_FINANCE`, `PO_AUDITOR` and then writing complex expressions that check both role and document state. With ABAC, the policy directly expresses the business rule: permit edit where resource.state == "DRAFT" and subject.id == resource.creatorId.

### Streaming and Long-Lived Sessions

Traditional request/response authorization checks once per request. But what about WebSocket connections that stay open for hours? What about Server-Sent Events streaming market data?

A user connects to a real-time dashboard. At connection time, they're authorized. An hour later, their account is suspended. Should they keep receiving data? With request-per-check models, the suspension takes effect on their next request. With streaming, there is no next request.

SAPL's reactive annotations (`@EnforceTillDenied`, `@EnforceDropWhileDenied`, `@EnforceRecoverableIfDenied`) subscribe to authorization decisions. When the decision changes from permit to deny, the stream responds immediately. The PDP continuously evaluates the policy as attributes change, and the PEP enforces the current decision on the live stream.

This also handles permission escalation and de-escalation. A support agent is granted temporary elevated access to debug an issue. The access has a time limit. When time expires, the policy decision changes, and active streams are affected without the agent taking any action.

### Beyond Permit/Deny

Sometimes the answer isn't yes or no. It's "yes, but."

A user can view customer records, but social security numbers must be masked. A user can export data, but the export must be logged to the audit system. A user can access the API, but rate-limited to 100 requests per hour.

These are obligations and advice attached to a permit decision. The policy doesn't just decide access. It specifies conditions and side effects. The enforcement point is responsible for executing them.

This keeps business logic in policy. The application doesn't need code paths for "if user is external, mask SSN." The policy says what to mask. The constraint handler does the masking. If the masking rules change, you update the policy. The application code doesn't change.

### When You Don't Need This

With all that said, SAPL adds complexity. A policy language is another thing to learn. A PDP is another component to operate.

If your authorization needs are simple and stable, stick with Spring Security's built-in annotations. "Users can read, admins can write" doesn't need ABAC. If you're building a prototype or internal tool with a handful of users, the overhead isn't justified.

SAPL pays off when authorization is a source of ongoing complexity. When you're frequently changing rules. When you're encoding business logic in security annotations. When you're copy-pasting role checks and adding special cases. When different deployments need different rules. That's when externalizing policy from code starts to make sense.

If that sounds like your situation, let's get started.

## Quick Start

Here's a complete example to show how the pieces fit together.

**1. Add the BOM and repository to your pom.xml:**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-bom</artifactId>
            <version>4.0.0-SNAPSHOT</version>
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

**2. Add the dependencies:**

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-security</artifactId>
</dependency>
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-pdp</artifactId>
</dependency>
```

**3. Configure the embedded PDP** in `application.properties`:

```properties
io.sapl.pdp.embedded.enabled=true
io.sapl.pdp.embedded.pdp-config-type=RESOURCES
io.sapl.pdp.embedded.policies-path=/policies
```

This tells SAPL to run a PDP inside your application and load policies from `src/main/resources/policies/`.

**4. Enable SAPL method security:**

```java
@Configuration
@EnableWebSecurity
@EnableSaplMethodSecurity  // for blocking applications
// or @EnableReactiveSaplMethodSecurity for WebFlux
public class SecurityConfig {
}
```

**5. Annotate a method:**

```java
@PreEnforce(subject = "authentication.name", action = "'read'", resource = "#id")
public Book findById(Long id) {
    return bookRepository.findById(id);
}
```

**6. Write a policy** (in `src/main/resources/policies/books.sapl`):

```
policy "users can read their own books"
permit action == "read"
where
    subject == resource.ownerId;
```

When someone calls `findById(42)`, SAPL checks if the authenticated user owns book 42. If yes, the method runs. If no, an `AccessDeniedException` is thrown.

That's the basic pattern: annotation tells SAPL what to check, policy decides the outcome.

## Method Security

Method security is where most applications start with SAPL. You annotate methods, and SAPL intercepts calls to enforce policies. This assumes you have `spring-boot-starter-web` (for servlet) or `spring-boot-starter-webflux` (for reactive) in your dependencies.

### Blocking Applications

For traditional Spring MVC applications, enable method security and use `@PreEnforce` or `@PostEnforce`:

```java
@Configuration
@EnableSaplMethodSecurity
public class SecurityConfig {
}
```

**@PreEnforce** checks authorization before the method runs:

```java
@PreEnforce
public void deleteBook(Long id) {
    bookRepository.deleteById(id);
}
```

If the policy returns `DENY`, the method never executes.

**@PostEnforce** checks authorization after the method runs, with access to the return value:

```java
@PostEnforce(resource = "returnObject")
public Book findById(Long id) {
    return bookRepository.findById(id);
}
```

This is useful when the decision depends on the returned data, or when you want the policy to transform the result. Note that the return object is serialized to JSON for the authorization subscription. Ensure your domain classes are Jackson-serializable, either through standard conventions or by adding Jackson annotations.

### Reactive Applications

For WebFlux applications, use the reactive variant:

```java
@Configuration
@EnableReactiveSaplMethodSecurity
public class SecurityConfig {
}
```

The same `@PreEnforce` and `@PostEnforce` annotations work, but they integrate with the reactive pipeline instead of blocking. For reactive methods, `@PostEnforce` only works with `Mono<>`, not `Flux<>`. The resource value must be a single object, not a stream.

Reactive applications also get three additional annotations for long-lived streams:

**@EnforceTillDenied** permits the stream until a deny decision arrives, then terminates it:

```java
@EnforceTillDenied
public Flux<StockPrice> streamPrices() {
    return priceService.stream();
}
```

**@EnforceDropWhileDenied** silently drops events during denied periods, but keeps the stream alive:

```java
@EnforceDropWhileDenied
public Flux<Message> streamMessages() {
    return messageService.stream();
}
```

**@EnforceRecoverableIfDenied** sends an error during denied periods, letting subscribers decide whether to continue:

```java
@EnforceRecoverableIfDenied
public Flux<Event> streamEvents() {
    return eventService.stream();
}
```

Use `@EnforceTillDenied` when denial should end the connection. Use `@EnforceDropWhileDenied` when the client shouldn't know events were skipped. Use `@EnforceRecoverableIfDenied` when the client needs to handle access changes gracefully.

One detail worth noting: enforcement begins when a subscriber subscribes to the returned Publisher, not when the method returns. If nobody subscribes, no authorization check happens.

### Building the Authorization Subscription

Every authorization check sends a subscription to the PDP with four components: subject (who is making the request), action (what they're trying to do), resource (what they're trying to access), and environment (contextual information like time or IP address).

By default, SAPL collects everything it can find, which creates verbose subscriptions. In practice, you'll want to be explicit:

```java
@PreEnforce(
    subject = "authentication.principal",
    action = "'delete'",
    resource = "#book"
)
public void deleteBook(Book book) { ... }
```

The values are Spring Expression Language (SpEL) expressions. You have access to `authentication` (the Spring Security Authentication object), `#paramName` (method parameters by name), `@beanName` (Spring beans), and `returnObject` (the method's return value, only in @PostEnforce).

Some examples:

```java
// Use the username as subject
subject = "authentication.name"

// Use a literal string as action
action = "'create-report'"

// Use a method parameter as resource
resource = "#orderId"

// Call a bean method
subject = "@userService.getCurrentUserProfile()"

// Build a custom object
resource = "{ 'type': 'book', 'id': #id }"
```

### Combining Annotations

You can use both `@PreEnforce` and `@PostEnforce` on the same method. Both must permit for the result to be returned:

```java
@PreEnforce(action = "'read'")
@PostEnforce(resource = "returnObject")
public Document getDocument(Long id) { ... }
```

However, some combinations are not allowed. You cannot mix SAPL annotations with Spring Security annotations like `@PreAuthorize`. You also cannot use `@EnforceTillDenied`, `@EnforceDropWhileDenied`, or `@EnforceRecoverableIfDenied` together with `@PreEnforce` or `@PostEnforce`.

## HTTP Request Security

Beyond method security, you can apply SAPL to the HTTP layer. This protects endpoints based on request attributes before any controller code runs.

For servlet applications:

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http, SaplAuthorizationManager sapl) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth.anyRequest().access(sapl))
        .build();
}
```

For reactive applications:

```java
@Bean
SecurityWebFilterChain filterChain(ServerHttpSecurity http, ReactiveSaplAuthorizationManager sapl) {
    return http
        .authorizeExchange(exchange -> exchange.anyExchange().access(sapl))
        .build();
}
```

The authorization manager constructs subscriptions from the HTTP request. Your policies can then check paths, headers, query parameters, and other request attributes.

This is useful for coarse-grained rules like "only employees can access /internal/*" without annotating every controller method.

## Constraints

So far we've talked about permit/deny decisions. But SAPL can do more. A decision can include constraints that the PEP must enforce.

There are three types. **Obligations** are mandatory. If the PEP cannot fulfill an obligation, it must deny access even if the decision was permit. Use obligations for things that must happen for the access to be valid. **Advice** is optional. The PEP should try to fulfill it, but failure doesn't block access. Use advice for nice-to-have actions like logging. **Resource transformation** replaces the returned data with a modified version from the policy. Use this to filter or redact sensitive fields.

A policy with constraints looks like this:

```
policy "permit with logging"
permit action == "read-salary"
obligation {
    "type": "logAccess",
    "message": "Salary data accessed"
}
advice {
    "type": "notify",
    "channel": "audit"
}
```

### Built-in Constraint Handlers

SAPL Spring Security includes handlers for common scenarios.

**ContentFilteringProvider** filters properties within returned objects. You can blacken (replace with XXX), delete, or replace specific JSON paths:

```
obligation {
    "type": "filterJsonContent",
    "actions": [
        { "type": "blacken", "path": "$.ssn" },
        { "type": "delete", "path": "$.salary" }
    ]
}
```

**ContentFilterPredicateProvider** filters items from collections based on conditions. This is useful for age-gating or classification-based filtering:

```
policy "filter content by age"
permit action == "list books"
obligation {
    "type": "jsonContentFilterPredicate",
    "conditions": [
        {
            "path": "$.ageRating",
            "type": "<=",
            "value": timeBetween(subject.birthday, dateOf(|<now>), "years")
        }
    ]
}
```

This example uses SAPL's built-in `timeBetween` and `dateOf` functions to calculate the user's age and filter out books with age ratings above that age.

The **r2dbcQueryManipulation** and **mongoQueryManipulation** modify database queries to filter results at the data layer. These are documented separately in [README_R2DBC.md](README_R2DBC.md) and [README_MONGO.md](README_MONGO.md).

### Writing Custom Handlers

When built-in handlers aren't enough, you write your own. A constraint handler is a Spring bean that implements a provider interface.

Here's a complete example that logs access attempts:

```java
@Component
public class LogAccessHandler implements RunnableConstraintHandlerProvider {

    private static final Logger log = LoggerFactory.getLogger(LogAccessHandler.class);

    @Override
    public boolean isResponsible(JsonNode constraint) {
        return constraint != null
            && constraint.has("type")
            && "logAccess".equals(constraint.get("type").asText());
    }

    @Override
    public Signal getSignal() {
        return Signal.ON_DECISION;
    }

    @Override
    public Runnable getHandler(JsonNode constraint) {
        String message = constraint.has("message")
            ? constraint.get("message").asText()
            : "Access logged";
        return () -> log.info(message);
    }
}
```

The `isResponsible` method checks if this handler should process a given constraint. The constraint is just a JSON object, so you define your own schema. The convention is to use a `type` field, but that's not required.

The `getSignal` method specifies when to run: `ON_DECISION`, `ON_EACH`, `ON_ERROR`, etc.

The `getHandler` method returns the actual logic to execute.

Spring auto-discovers handlers as beans. Just annotate with `@Component` and implement the right interface.

The available provider interfaces cover different scenarios:

- `RunnableConstraintHandlerProvider` - run code at a specific signal
- `ConsumerConstraintHandlerProvider` - process objects flowing through the stream
- `MethodInvocationConstraintHandlerProvider` - modify method arguments before invocation
- `FilterPredicateConstraintHandlerProvider` - filter collections based on predicates
- `ErrorHandlerProvider` - handle or transform exceptions
- `SubscriptionHandlerProvider` - hook into reactive stream subscription signals
- `RequestHandlerProvider` - act on the AuthorizationDecision itself

Pick the interface that matches what you need to do.

## Query Manipulation

For applications using Spring Data, SAPL can modify database queries to filter results based on policies. Instead of fetching all data and filtering in memory, the filter conditions are pushed into the query itself.

This works with reactive MongoDB and R2DBC. You annotate repository methods with `@QueryEnforce`, and policies return query manipulation obligations.

Example repository:

```java
@Repository
public interface BookRepository extends ReactiveCrudRepository<Book, Long> {

    @QueryEnforce(action = "findAll", subject = "{\"userId\": #{principal.id}}")
    Flux<Book> findAll();
}
```

Example policy:

```
policy "filter by department"
permit action == "findAll"
obligation {
    "type": "r2dbcQueryManipulation",
    "conditions": ["department_id = " + subject.departmentId]
}
```

The query gets a WHERE clause appended, so users only see data they're allowed to see.

For complete documentation, see [MongoDB Query Manipulation](README_MONGO.md) and [R2DBC Query Manipulation](README_R2DBC.md).

## Configuration

SAPL Spring Security is configured through `application.properties` or `application.yml`. The properties control which PDP to use and how it behaves.

### Embedded PDP

The embedded PDP runs inside your application. Policies are loaded from bundled resources or a filesystem directory.

```properties
# Enable the embedded PDP (required)
io.sapl.pdp.embedded.enabled=true

# Where to load policies from: RESOURCES, DIRECTORY, MULTI_DIRECTORY, or BUNDLES
io.sapl.pdp.embedded.pdp-config-type=RESOURCES

# Path to policies (in resources or filesystem)
io.sapl.pdp.embedded.policies-path=/policies

# Path to pdp.json configuration (combining algorithm, variables)
io.sapl.pdp.embedded.config-path=/policies
```

The full list of embedded PDP properties:

| Property | Default | Description |
|----------|---------|-------------|
| `io.sapl.pdp.embedded.enabled` | `true` | Enable or disable the embedded PDP |
| `io.sapl.pdp.embedded.pdp-config-type` | `RESOURCES` | `RESOURCES` loads from classpath, `DIRECTORY` loads from disk and watches for changes, `MULTI_DIRECTORY` for multi-tenant subdirectories, `BUNDLES` for multi-tenant .saplbundle files |
| `io.sapl.pdp.embedded.policies-path` | `/policies` | Directory containing `.sapl` policy files |
| `io.sapl.pdp.embedded.config-path` | `/policies` | Directory containing `pdp.json` configuration |
| `io.sapl.pdp.embedded.index` | `NAIVE` | Index algorithm: `NAIVE` for small policy sets, `CANONICAL` for large ones |
| `io.sapl.pdp.embedded.print-trace` | `false` | Log full JSON evaluation trace (verbose, for debugging) |
| `io.sapl.pdp.embedded.print-json-report` | `false` | Log JSON decision report |
| `io.sapl.pdp.embedded.print-text-report` | `false` | Log human-readable decision report |
| `io.sapl.pdp.embedded.pretty-print-reports` | `false` | Format JSON in reports |

For development, `RESOURCES` is convenient because policies are bundled in the JAR. For production with dynamic policy updates, use `DIRECTORY` and point to a directory that can be updated without redeployment. For multi-tenant deployments, use `MULTI_DIRECTORY` (one subdirectory per tenant) or `BUNDLES` (one .saplbundle file per tenant).

### Remote PDP

The remote PDP connects to an external PDP server (like SAPL Node). Use this when policies are managed centrally or when multiple applications share the same policies.

```properties
# Enable the remote PDP
io.sapl.pdp.remote.enabled=true

# Connection type
io.sapl.pdp.remote.type=http

# HTTP URL of the PDP server
io.sapl.pdp.remote.host=https://pdp.example.org:8443

# Authentication (choose one)
# Basic auth:
io.sapl.pdp.remote.key=myapp
io.sapl.pdp.remote.secret=secret123

# Or API key:
io.sapl.pdp.remote.api-key=your-api-key
```

The full list of remote PDP properties:

| Property | Default | Description |
|----------|---------|-------------|
| `io.sapl.pdp.remote.enabled` | `false` | Enable or disable the remote PDP |
| `io.sapl.pdp.remote.type` | `http` | Connection type |
| `io.sapl.pdp.remote.host` | | HTTP URL of the PDP server |
| `io.sapl.pdp.remote.key` | | Username for basic authentication |
| `io.sapl.pdp.remote.secret` | | Password for basic authentication |
| `io.sapl.pdp.remote.api-key` | | API key for token authentication |
| `io.sapl.pdp.remote.ignore-certificates` | `false` | Skip TLS certificate validation (not for production) |

You must configure exactly one authentication method: either `key` and `secret` together, or `api-key` alone.

## Common Questions

**How does this differ from @PreAuthorize?**

Spring's `@PreAuthorize` evaluates a SpEL expression at runtime. The logic is in your Java code. SAPL evaluates external policy files. The logic is separate from your code. This matters when policies change frequently, when non-developers need to review rules, or when the same policies apply across multiple applications.

**What's the performance impact?**

Each authorization check calls the PDP. With an embedded PDP, this is an in-memory call, typically sub-millisecond. With a remote PDP, there's network latency. The PDP caches policy evaluation, so repeated similar requests are fast. For most applications, the overhead is negligible compared to database or network I/O.

**Can I use SAPL alongside @PreAuthorize?**

On different methods, yes. On the same method, no. SAPL annotations and Spring Security annotations cannot be combined on a single method.

**What happens if the PDP is unavailable?**

With an embedded PDP, this isn't an issue since it's part of your application. With a remote PDP, you configure the behavior: deny by default, permit by default, or use cached decisions. The safe default is deny.

**Where do policy files go?**

By default, `src/main/resources/policies/`. The embedded PDP loads from this path when `pdp-config-type=RESOURCES`. If you use `DIRECTORY`, specify an absolute path and the PDP will watch for changes.

## Next Steps

The best way to learn is to try it. Start with method security on one or two endpoints. Write simple permit/deny policies. Once that works, try adding obligations to see how constraints work.

For more details:

- [SAPL Documentation](https://sapl.io/docs) - policy language reference
- [sapl-demos](https://github.com/heutelbeck/sapl-demos) - example applications
- [README_MONGO.md](README_MONGO.md) - MongoDB query manipulation
- [README_R2DBC.md](README_R2DBC.md) - R2DBC query manipulation
