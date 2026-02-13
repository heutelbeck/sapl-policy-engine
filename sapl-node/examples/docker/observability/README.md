# SAPL Node Observability Demo

Live monitoring of SAPL PDP authorization decisions with Prometheus and Grafana.

## What You Get

- **sapl-node** running with sample policies producing PERMIT and DENY decisions
- **Prometheus** scraping PDP metrics every 5 seconds
- **Grafana** with a pre-built dashboard showing:
  - Decision rate by outcome (PERMIT/DENY/INDETERMINATE/NOT_APPLICABLE)
  - Decision distribution pie chart
  - Active subscription count
  - First decision latency
  - Subscription lifetime
- **Load generator** sending varied authorization requests

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker with Compose v2

## Quick Start

```bash
cd sapl-node/examples/docker/observability
./start.sh
```

This will:
1. Build the sapl-node JAR (if not already built)
2. Start Prometheus and Grafana containers
3. Start sapl-node with metrics enabled
4. Start the load generator

## URLs

| Service         | URL                                      | Credentials   |
|-----------------|------------------------------------------|---------------|
| Grafana         | http://localhost:3000                    | admin / admin |
| Prometheus      | http://localhost:9090                    |               |
| sapl-node       | http://localhost:8080                    | no auth       |
| Health check    | http://localhost:8080/actuator/health    |               |
| Raw metrics     | http://localhost:8080/actuator/prometheus |               |

## Manual Requests

```bash
# PERMIT: read access to documents
curl -X POST http://localhost:8080/api/pdp/decide-once \
  -H 'Content-Type: application/json' \
  -d '{"subject":"alice","action":"read","resource":"documents"}'

# DENY: read access to admin resource (deny-admin-resources policy)
curl -X POST http://localhost:8080/api/pdp/decide-once \
  -H 'Content-Type: application/json' \
  -d '{"subject":"alice","action":"read","resource":"admin-panel"}'

# DENY: write access (no matching permit policy, default decision)
curl -X POST http://localhost:8080/api/pdp/decide-once \
  -H 'Content-Type: application/json' \
  -d '{"subject":"charlie","action":"write","resource":"documents"}'
```

## Policies

Two policies in `policies/` with `PRIORITY_DENY` combining algorithm:

- **permit-read** -- permits any `action == "read"` request
- **deny-admin-resources** -- denies any `resource =~ "admin.*"` request

The combining algorithm means: if any deny matches, the result is DENY regardless of permits. If only permits match, the result is PERMIT. If nothing matches, the default decision (DENY) applies.

## Metrics Reference

| Metric | Type | Description |
|--------|------|-------------|
| `sapl_decisions_total` | counter | Decisions by outcome (`decision` label) |
| `sapl_subscriptions_active` | gauge | Currently active subscriptions |
| `sapl_decision_first_latency_seconds` | timer | Time to first decision |
| `sapl_subscription_duration_seconds` | timer | Subscription lifetime |

## Stop

Press `Ctrl+C` in the terminal running `start.sh`, or:

```bash
./stop.sh
```

## Configuration

The demo uses `config/application.yml` which:
- Disables TLS and authentication (demo only)
- Enables PDP metrics (`io.sapl.pdp.embedded.metrics-enabled: true`)
- Enables text reports for decision logging

Edit the policies in `policies/` while the demo is running to see hot-reload in action.
