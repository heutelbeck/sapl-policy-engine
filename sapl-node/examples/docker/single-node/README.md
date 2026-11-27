# SAPL Node Single-Node Docker Example

This example demonstrates a simple SAPL Node deployment with Basic Authentication.

## Prerequisites

- Docker and Docker Compose installed
- Access to the SAPL Node container image

## Quick Start

1. **Start the server:**
   ```bash
   docker compose up -d
   ```

2. **Wait for the server to be ready:**
   ```bash
   docker compose logs -f sapl-node
   # Look for "Started SaplNodeApplication"
   ```

3. **Test with curl:**
   ```bash
   # This should return PERMIT (matches the example policy)
   curl -X POST http://localhost:8080/api/pdp/decide \
     -u 'xwuUaRD65G:3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_' \
     -H "Content-Type: application/json" \
     -d '{"subject":"user","action":"read","resource":"document"}'

   # This should return DENY (no matching policy)
   curl -X POST http://localhost:8080/api/pdp/decide \
     -u 'xwuUaRD65G:3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_' \
     -H "Content-Type: application/json" \
     -d '{"subject":"user","action":"delete","resource":"secret"}'
   ```

4. **Stop the server:**
   ```bash
   docker compose down
   ```

## Configuration

### Authentication

This example uses HTTP Basic Authentication. The demo user credentials are:
- Username: `xwuUaRD65G`
- Password: `3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_`

**IMPORTANT:** For production, generate your own credentials using the SAPL Node CLI:
```bash
docker run --rm ghcr.io/heutelbeck/sapl-node:4.2.0-SNAPSHOT generate basic
```

### Policies

Policies are located in the `./policies/` directory:
- `pdp.json`: PDP configuration (combining algorithm, variables)
- `example-policy.sapl`: Sample SAPL policy

The server monitors this directory for changes. Modify the `.sapl` files to see hot-reload in action.

### TLS

This demo serves plaintext, allowed explicitly with `IO_SAPL_NODE_ALLOWNOTLS=true`. The node logs a warning at every start. For production:

1. Obtain a certificate and its private key as PEM files
2. Mount them and name them via environment variables, then remove the opt-in:
   ```yaml
   environment:
     - IO_SAPL_NODE_TLS_CERTIFICATECHAIN=/pdp/tls/server.pem
     - IO_SAPL_NODE_TLS_PRIVATEKEY=/pdp/tls/server.key
   ```

Both APIs serve the certificate, and a renewed certificate is served without a restart. See `examples/docker/with-tls` for a complete setup.

## Customization

Edit `docker-compose.yml` to:
- Add more users
- Enable API key authentication
- Configure OAuth2 (requires additional setup)
- Adjust logging levels
- Set up rate limiting

## Troubleshooting

### Container won't start
Check logs: `docker compose logs sapl-node`

### Authentication fails
Verify the password hash matches the plaintext password. Use the `generate basic` CLI command.

### Policies not loading
Ensure the `policies` directory contains valid `.sapl` files and a `pdp.json`. A definitively broken configuration is not dropped silently. It drives the PDP to the `ERROR` state and it fails closed, returning `INDETERMINATE`. Check `GET /actuator/health` for the per-PDP `state` and `lastError` reason.
