# SAPL Node Test Configurations

Test setups for sapl-node covering the three policy source modes and multi-tenant routing.

All setups share the same authentication configuration (basic auth, API key, no-auth)
and run on `localhost:8080` with SSL disabled.

## Prerequisites

Build the sapl-node jar:

```bash
mvn package -pl sapl-node -q -DskipTests
```

The jar is at `sapl-node/target/sapl-node-4.0.0-SNAPSHOT.jar` (relative to the repository root).


## 1. Single Directory (`singledirectory/`)

**Mode:** `DIRECTORY` (default) -- single-tenant, file-watching

Monitors a single directory for `.sapl` files and `pdp.json`. Changes are picked up
automatically at runtime. No multi-tenant routing -- all requests use the same policy set.

```
singledirectory/
  config/application.yml
  pdp.json              -- PRIORITY_DENY, default DENY, errors ABSTAIN
  permitall.sapl        -- permits everything
  policy_A.sapl         -- deny when resource=="foo" and subject=="WILLI"
  policy_B.sapl         -- permit when resource=="foo" and subject=="WILLI"
  policy_C.sapl         -- permit with streaming time attribute
  policySetLoading.sapltest  -- integration test demo for sapltest DSL
```

**Start:**

```bash
cd sapl-node/examples/local/singledirectory
java -jar ../../../target/sapl-node-4.0.0-SNAPSHOT.jar
```

**Test:**

```bash
# WILLI reads foo -- DENY (policy_A denies, PRIORITY_DENY)
curl -s http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"WILLI","action":"read","resource":"foo"}' | jq .

# anyone reads anything -- PERMIT (permitall matches)
curl -s http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"alice","action":"read","resource":"bar"}' | jq .
```


## 2. Multi-Directory (`multidirectory/`)

**Mode:** `MULTI_DIRECTORY` -- multi-tenant, file-watching, no bundle security

Each subdirectory under the base path becomes a tenant (pdpId). Directory name = pdpId.
Changes to any tenant's files are picked up automatically.

```
multidirectory/
  config/application.yml
  tenants/
    default/             -- pdpId: "default"
      pdp.json           -- PRIORITY_DENY, default DENY, errors ABSTAIN
      permitall.sapl     -- permits everything
    production/          -- pdpId: "production"
      pdp.json           -- PRIORITY_DENY, default DENY, errors PROPAGATE
      admin-access.sapl  -- permit when subject=="admin"
      read-access.sapl   -- permit when action=="read"
      strict-policy.sapl -- deny when action=="delete"
    staging/             -- pdpId: "staging"
      pdp.json           -- PRIORITY_PERMIT, default DENY, errors PROPAGATE
      permissive-policy.sapl       -- permit (currently: permit false -- inactive)
      deny-delete-prod-data.sapl   -- deny delete on production-data
```

**Start:**

```bash
cd sapl-node/examples/local/multidirectory
java -jar ../../../target/sapl-node-4.0.0-SNAPSHOT.jar
```

**Tenant routing** uses client-to-tenant binding in `application.yml`. Each authenticated
client is bound to a specific pdpId. Unauthenticated requests fall back to
`defaultPdpId: "default"`.

**Test:**

```bash
# Default tenant -- PERMIT (permitall)
curl -s http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"anyone","action":"anything","resource":"anything"}' | jq .

# Production -- admin reads: PERMIT
curl -s -H "Authorization: Bearer sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j" \
  http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"admin","action":"read","resource":"data"}' | jq .

# Production -- admin deletes: DENY (strict-policy denies, PRIORITY_DENY wins)
curl -s -H "Authorization: Bearer sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j" \
  http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"admin","action":"delete","resource":"database"}' | jq .

# Staging -- alice reads: DENY (permissive-policy has "permit false", no permit matches)
curl -s -H "Authorization: Bearer sapl_oCR3QQ8fhD_XYs3x1dQ3M1NM9FJLjPHlwd1NXiMdZ1f" \
  http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"alice","action":"read","resource":"document"}' | jq .
```


## 3. Bundles (`bundles/`)

**Mode:** `BUNDLES` -- multi-tenant, Ed25519 signed bundles, per-tenant key isolation

Each `.saplbundle` file in the bundles directory is a ZIP archive containing policies and
`pdp.json`. The filename (without extension) becomes the pdpId. Bundle contents are
cryptographically signed with Ed25519 and verified on load.

```
bundles/
  config/application.yml
  bundles/                  -- watched directory
    default.saplbundle      -- signed with default-key
    production.saplbundle   -- signed with production-key
    staging.saplbundle      -- signed with staging-key
  unsigned/
    production-unsigned.saplbundle  -- unsigned copy for testing rejection
  keys/
    default-key.pem / .pub
    production-key.pem / .pub
    staging-key.pem / .pub
  policies/                 -- source policies (used to build bundles)
    default/    ...
    production/ ...
    staging/    ...
```

### Security Configuration

```yaml
bundle-security:
  keys:                          # key catalogue: keyId -> base64 public key
    default-key:    "MCowBQY..."
    production-key: "MCowBQY..."
    staging-key:    "MCowBQY..."
  tenants:                       # tenant -> trusted keyIds
    default:    ["default-key"]
    production: ["production-key"]
    staging:    ["staging-key"]
  unsigned-tenants:              # tenants that accept unsigned bundles
    - "staging"
```

**Security model:**

- Each tenant is bound to specific signing keys via `tenants`
- Cross-tenant replay is rejected (a bundle signed with `staging-key` fails for `production`)
- `unsigned-tenants` lists tenants that may load unsigned bundles (per-tenant opt-out)
- `staging` is in `unsigned-tenants` -- unsigned staging bundles are accepted
- `production` and `default` are NOT in `unsigned-tenants` -- must be signed

**Start:**

```bash
cd sapl-node/examples/local/bundles
java -jar ../../../target/sapl-node-4.0.0-SNAPSHOT.jar
```

**Test:**

```bash
# Default tenant -- PERMIT (permitall, signed with default-key)
curl -s http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"anyone","action":"anything","resource":"anything"}' | jq .

# Production -- admin reads: PERMIT
curl -s -H "Authorization: Bearer sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j" \
  http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"admin","action":"read","resource":"data"}' | jq .

# Production -- admin deletes: DENY (PRIORITY_DENY, strict-policy denies)
curl -s -H "Authorization: Bearer sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j" \
  http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"admin","action":"delete","resource":"data"}' | jq .

# Staging -- alice reads: DENY (permissive-policy has "permit false", no permit matches)
curl -s -H "Authorization: Bearer sapl_oCR3QQ8fhD_XYs3x1dQ3M1NM9FJLjPHlwd1NXiMdZ1f" \
  http://localhost:8080/api/pdp/decide \
  -H "Content-Type: application/json" \
  -d '{"subject":"alice","action":"read","resource":"document"}' | jq .
```

### Security Tests

```bash
NODE_JAR=../../../target/sapl-node-4.0.0-SNAPSHOT.jar

# 1. Unsigned bundle for production -- REJECTED (not in unsigned-tenants)
cp unsigned/production-unsigned.saplbundle bundles/production.saplbundle

# 2. Unsigned bundle for staging -- ACCEPTED (staging is in unsigned-tenants)
java -jar $NODE_JAR bundle create \
  -i policies/staging \
  -o bundles/staging.saplbundle

# 3. Cross-tenant: sign production with staging key -- REJECTED
java -jar $NODE_JAR bundle create \
  -i policies/production \
  -k keys/staging-key.pem --key-id staging-key \
  -o bundles/production.saplbundle

# Restore production (signed with correct key)
java -jar $NODE_JAR bundle create \
  -i policies/production \
  -k keys/production-key.pem --key-id production-key \
  -o bundles/production.saplbundle

# Restore staging (signed)
java -jar $NODE_JAR bundle create \
  -i policies/staging \
  -k keys/staging-key.pem --key-id staging-key \
  -o bundles/staging.saplbundle
```

### Bundle CLI

```bash
NODE_JAR=../../../target/sapl-node-4.0.0-SNAPSHOT.jar

# Generate a new Ed25519 keypair
java -jar $NODE_JAR bundle keygen -o keys/new-key

# Create a signed bundle from a policy directory
java -jar $NODE_JAR bundle create \
  -i policies/production \
  -k keys/production-key.pem --key-id production-key \
  -o bundles/production.saplbundle

# Verify a bundle against a public key
java -jar $NODE_JAR bundle verify \
  -b bundles/production.saplbundle \
  -k keys/production-key.pub

# Inspect bundle contents and metadata
java -jar $NODE_JAR bundle inspect \
  -b bundles/production.saplbundle
```


## Tenant Policies

All three multi-tenant setups (multidirectory, bundles) share the same policy logic:

| Tenant     | Algorithm       | Policies                                 | Behavior                                                                  |
|------------|-----------------|------------------------------------------|---------------------------------------------------------------------------|
| default    | PRIORITY_DENY   | permitall                                | Permits everything                                                        |
| production | PRIORITY_DENY   | admin-access, read-access, strict-policy | Read: permit. Admin: full access. Delete: always denied (deny overrides)  |
| staging    | PRIORITY_PERMIT | permissive-policy, deny-delete-prod-data | Permissive (permit overrides deny). Delete of production-data: denied     |

Note: `staging/permissive-policy.sapl` currently contains `permit false` which never matches.
With PRIORITY_PERMIT and default DENY, staging denies everything unless another policy permits.


## Authentication

All setups accept three authentication methods:

| Method  | Credentials                                          | Tenant                                         |
|---------|------------------------------------------------------|------------------------------------------------|
| No auth | (none)                                               | Routes to `defaultPdpId` ("default")           |
| Basic   | `xwuUaRD65G` / `3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_`    | default                                        |
| API key | `sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j`   | default (single) or production (multi/bundles) |
| API key | `sapl_oCR3QQ8fhD_XYs3x1dQ3M1NM9FJLjPHlwd1NXiMdZ1f`   | default (single) or staging (multi/bundles)    |

Client-to-tenant bindings differ between setups. Check each `application.yml` for details.