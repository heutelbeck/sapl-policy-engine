# SAPL Node Interactive Test Scripts

This directory contains bash scripts for manually testing and experimenting with SAPL Node. These scripts complement the automated integration tests by allowing interactive exploration.

## Prerequisites

- A running SAPL Node instance
- `curl` installed
- `jq` installed for JSON formatting

## Available Scripts

### test-no-auth.sh

Tests SAPL Node without authentication (for development/testing environments).

```bash
./test-no-auth.sh                        # Default: http://localhost:8443
./test-no-auth.sh https://localhost:8443 # Custom URL
```

### test-basic-auth.sh

Tests SAPL Node with HTTP Basic Authentication.

```bash
./test-basic-auth.sh USERNAME PASSWORD [BASE_URL]

# Example with test credentials:
./test-basic-auth.sh xwuUaRD65G '3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_'
```

### test-api-key.sh

Tests SAPL Node with API Key Authentication.

```bash
./test-api-key.sh API_KEY [BASE_URL]

# Example:
./test-api-key.sh sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j
```

### test-streaming.sh

Tests streaming subscriptions that receive live policy updates.

```bash
./test-streaming.sh noauth
./test-streaming.sh basic "username:password" https://localhost:8443
./test-streaming.sh apikey "sapl_..." https://localhost:8443
```

**Tip:** Run this script and then modify a policy file. You should see the updated decision streamed to your terminal.

### test-multi-tenant.sh

Tests multi-tenant pdpId routing with different users.

```bash
./test-multi-tenant.sh [BASE_URL]
```

This script prompts for credentials for two different tenants and demonstrates that the same request produces different decisions based on the user's assigned pdpId.

## Running the Scripts

1. Make scripts executable:
   ```bash
   chmod +x *.sh
   ```

2. Start SAPL Node with appropriate configuration:
   ```bash
   # For no-auth testing:
   docker run -p 8443:8443 \
     -e IO_SAPL_NODE_ALLOWNOAUTH=true \
     -e SERVER_SSL_ENABLED=false \
     ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
   ```

3. Run a test script:
   ```bash
   ./test-no-auth.sh http://localhost:8443
   ```

## Common Test Subscription

All scripts use this default test subscription:
```json
{
  "subject": "Willi",
  "action": "eat",
  "resource": "apple"
}
```

With the default test policy (`permit action == "eat" where resource == "apple"`), this should return `PERMIT`.

## Troubleshooting

### SSL Certificate Errors

The scripts use `-k` flag to skip SSL verification for self-signed certificates. For production environments, remove this flag and configure proper certificates.

### Connection Refused

Verify SAPL Node is running and listening on the expected port:
```bash
curl -k https://localhost:8443/actuator/health
```

### Authentication Failures

- Basic Auth: Verify username/password match the configured user entry
- API Key: Ensure the key starts with `sapl_` prefix and matches the encoded version in configuration
