# SAPL Node with Keycloak OAuth2 Integration

This example demonstrates SAPL Node deployment with Keycloak for OAuth2/JWT authentication and multi-tenant support via JWT claims.

## Architecture

```
+----------------+       +----------------+       +----------------+
|    Client      | ----> |   Keycloak     | ----> |   SAPL Node    |
|                |       |  (OAuth2/OIDC) |       |  (Resource     |
|                | <---- |                | <---- |   Server)      |
+----------------+       +----------------+       +----------------+
        |                       ^                        ^
        |                       |                        |
        +------- 1. Get JWT ----+                        |
        |                                                |
        +------- 2. Authorization Request ---------------+
                 (with JWT in Authorization header)
```

## Quick Start

1. **Start the services:**
   ```bash
   docker compose up -d
   ```

2. **Wait for services to be ready:**
   ```bash
   # Check Keycloak (wait for healthy)
   docker compose ps

   # Check SAPL Node logs
   docker compose logs -f sapl-node
   ```

3. **Get an access token from Keycloak:**
   ```bash
   # Using Resource Owner Password flow (for demo only)
   TOKEN=$(curl -s -X POST http://localhost:8080/realms/sapl-demo/protocol/openid-connect/token \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "grant_type=password" \
     -d "client_id=sapl-client" \
     -d "client_secret=sapl-client-secret" \
     -d "username=production-user" \
     -d "password=production123" \
     | jq -r '.access_token')

   echo "Token: ${TOKEN:0:50}..."
   ```

4. **Make an authorization request:**
   ```bash
   curl -X POST http://localhost:8443/api/pdp/decide \
     -H "Authorization: Bearer ${TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{"subject":"user","action":"read","resource":"document"}'
   ```

## Users and PDP IDs

The Keycloak realm is pre-configured with these users:

| Username | Password | sapl_pdp_id Claim |
|----------|----------|-------------------|
| production-user | production123 | production |
| staging-user | staging123 | staging |
| default-user | default123 | (none - uses defaultPdpId) |

## JWT Claim Configuration

The `sapl_pdp_id` claim is added to JWT tokens via a Keycloak protocol mapper:

1. The user has a `sapl_pdp_id` attribute set in Keycloak
2. A protocol mapper copies this attribute to the JWT access token
3. SAPL Node extracts the claim and uses it to route to the correct PDP configuration

### Custom Claim Name

To use a different claim name, update both:

1. **Keycloak:** Modify the protocol mapper's `claim.name` in the realm configuration
2. **SAPL Node:** Set `IO_SAPL_NODE_OAUTH_PDPIDCLAIM=your_custom_claim`

## Multi-Tenant Setup with OAuth2

To use multi-tenant directory routing with OAuth2:

1. Enable `MULTI_DIRECTORY` source type:
   ```yaml
   - IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE=MULTI_DIRECTORY
   ```

2. Create directories matching the JWT claim values:
   ```
   policies/
     production/
       pdp.json
       policies.sapl
     staging/
       pdp.json
       policies.sapl
   ```

3. Users with `sapl_pdp_id=production` will be routed to the production policies.

## Missing Claim Handling

Configure what happens when the JWT lacks the `sapl_pdp_id` claim:

- `IO_SAPL_NODE_REJECTONMISSINGPDPID=true`: Return 401 Unauthorized
- `IO_SAPL_NODE_REJECTONMISSINGPDPID=false`: Use `defaultPdpId`

## Keycloak Administration

Access the Keycloak admin console:
- URL: http://localhost:8080/admin
- Username: admin
- Password: admin

From here you can:
- Create additional users
- Modify user attributes
- Configure client settings
- View/modify protocol mappers

## Production Considerations

1. **TLS:** Enable HTTPS for Keycloak in production
2. **Secrets:** Use proper secret management (not hardcoded in compose files)
3. **Token Lifetime:** Adjust token lifetimes based on security requirements
4. **Issuer URI:** Use the actual Keycloak URL (not `keycloak` hostname)
