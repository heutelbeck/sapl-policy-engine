# SAPL Spring Security

Policy-based authorization for Spring Boot. Write access control rules as external SAPL policy files and enforce them at runtime through annotations like `@PreEnforce` and `@PostEnforce`. Policies can be updated without code changes or redeployment.

## How It Works

Your application annotates methods with enforcement annotations. SAPL intercepts the call, sends an authorization subscription to the Policy Decision Point (PDP), and enforces the decision, including any obligations or advice the policy attaches.

```java
@PreEnforce(subject = "authentication.name", action = "'read'", resource = "#id")
public Book findById(Long id) {
    return bookRepository.findById(id);
}
```

```
policy "users can read their own books"
permit
  action == "read";
  subject == resource.ownerId
```

If the PDP permits, the method runs. If not, an `AccessDeniedException` is thrown. If the decision carries obligations (like access logging or field redaction), they are enforced automatically through registered constraint handlers.

## What You Get

SAPL goes beyond simple permit/deny. Decisions can carry obligations that must be fulfilled, advice that should be attempted, and resource transformations that modify return values before they reach the caller. The library handles all of this transparently.

For reactive applications, streaming annotations (`@EnforceTillDenied`, `@EnforceDropWhileDenied`, `@EnforceRecoverableIfDenied`) maintain a live connection to the PDP, so access rights update in real time as policies, attributes, or the environment change. Transaction integration ensures that obligation failures after a database write trigger a rollback. Built-in constraint handlers cover common needs like JSON field redaction, collection filtering, and database query manipulation for R2DBC and MongoDB. Writing custom handlers is as simple as implementing an interface and registering a Spring bean.

## Getting Started

Add the dependency via the SAPL BOM:

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
```

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-security</artifactId>
</dependency>
```

For setup instructions, configuration options, the constraint handler reference, and the full API, see the [Spring Security documentation](https://sapl.io/docs/latest/6_4_SpringIntegration/).

## Links

- [Full Documentation](https://sapl.io/docs/latest/)
- [Spring Security Integration](https://sapl.io/docs/latest/6_4_SpringIntegration/)
- [Demo Applications](https://github.com/heutelbeck/sapl-demos)
- [Report an Issue](https://github.com/heutelbeck/sapl-policy-engine/issues)

## License

Apache-2.0
