# SAPL Node Multi-Tenant Docker Example

This example demonstrates a multi-tenant SAPL Node deployment where each tenant has isolated policy configurations.

## Architecture

```
policies/
  tenant-a/            # Restrictive tenant (deny by default)
    pdp.json
    restrictive-policy.sapl
  tenant-b/            # Permissive tenant (permit by default)
    pdp.json
    permissive-policy.sapl
```

Each user is assigned a `pdpId` that maps to a tenant directory. The server automatically routes authorization requests to the correct tenant's policies.

## Quick Start

1. **Start the server:**
   ```bash
   docker compose up -d
   ```

2. **Test Tenant A (restrictive - expects DENY):**
   ```bash
   curl -k -X POST https://localhost:8443/api/pdp/decide \
     -u "tenant-a-user:tenantApass123!" \
     -H "Content-Type: application/json" \
     -d '{"subject":"user","action":"read","resource":"data"}'
   ```

3. **Test Tenant B (permissive - expects PERMIT):**
   ```bash
   curl -k -X POST https://localhost:8443/api/pdp/decide \
     -u "tenant-b-user:tenantBpass123!" \
     -H "Content-Type: application/json" \
     -d '{"subject":"user","action":"read","resource":"data"}'
   ```

4. **Verify tenant isolation:**
   The same authorization request returns different decisions based on which user (tenant) makes the request.

## Configuration

### Adding a New Tenant

1. Create a new directory under `policies/`:
   ```bash
   mkdir -p policies/tenant-c
   ```

2. Add `pdp.json` and `.sapl` policy files

3. Add a user in `docker-compose.yml`:
   ```yaml
   - IO_SAPL_NODE_USERS_2_ID=tenant-c-client
   - IO_SAPL_NODE_USERS_2_PDPID=tenant-c
   - IO_SAPL_NODE_USERS_2_BASIC_USERNAME=tenant-c-user
   - IO_SAPL_NODE_USERS_2_BASIC_SECRET=<encoded-password>
   ```

4. Restart the server (or wait for hot-reload)

### Tenant Isolation

- Each tenant's policies are completely isolated
- Changes to one tenant's policies do not affect other tenants
- A user can only access their assigned tenant's policies
- The `pdpId` is bound at authentication time and cannot be changed per-request

## Use Cases

- **SaaS Platforms:** Different customers with different authorization requirements
- **Environment Separation:** Dev/staging/production with different policies
- **Department Isolation:** Different business units with separate access rules
- **Compliance:** Different regulatory requirements per tenant

## Troubleshooting

### User gets wrong tenant's policies
Verify the `pdpId` in the user configuration matches the directory name.

### New tenant not recognized
Ensure the directory exists and contains at least one `.sapl` file. Check server logs for configuration loading messages.

### Tenant isolation not working
Verify `IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE=MULTI_DIRECTORY` is set. Without this, all policies are loaded as a single PDP.
