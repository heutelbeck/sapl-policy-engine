---
layout: default
title: Monitoring
parent: SAPL Node
nav_order: 707
---

## Monitoring and Observability

SAPL Node exposes health, metrics, and decision data through standard Spring Boot Actuator and Micrometer interfaces. There is no proprietary monitoring agent. Use your existing observability stack (Prometheus, Grafana, Loki, ELK, or any tool that consumes these standard interfaces).

### PDP Health Indicator

The PDP reports one of three operational states:

| State | Meaning | Health Status |
|-------|---------|---------------|
| `LOADED` | Policies compiled and active. The PDP is fully operational. | UP |
| `STALE` | A hot reload failed, but the PDP is still serving decisions from the previous valid configuration. | UP (with warning) |
| `ERROR` | No valid configuration loaded. The PDP cannot make valid authorization decisions and serves INDETERMINATE. | DOWN |

In multi tenant deployments, the health indicator aggregates the state of all PDP instances. If all instances are `LOADED`, health is UP. If any instance is `STALE` while none are `ERROR`, health is UP with a warning detail. If any instance is `ERROR`, health is DOWN.

The health endpoint returns detail fields for each PDP instance:

| Field | Description |
|-------|-------------|
| `state` | Current operational state (`LOADED`, `STALE`, or `ERROR`). |
| `configurationId` | Identifier of the active configuration. Absent in `ERROR` state. |
| `combiningAlgorithm` | The combining algorithm in use, with `votingMode`, `defaultDecision`, and `errorHandling` fields. Absent in `ERROR` state. |
| `documentCount` | Number of SAPL documents in the active configuration. |
| `lastSuccessfulLoad` | Timestamp of the last successful configuration load. |
| `lastFailedLoad` | Timestamp of the last failed configuration load. Absent if no failure occurred. |
| `lastError` | Error message from the last failed load. Absent if no failure occurred. |

Example health response with one loaded and one stale PDP:

```json
{
  "status": "UP",
  "components": {
    "pdp": {
      "status": "UP",
      "details": {
        "warning": "One or more PDPs are serving stale policies",
        "pdps": {
          "default": {
            "state": "LOADED",
            "configurationId": "v42",
            "combiningAlgorithm": {
              "votingMode": "PRIORITY_PERMIT",
              "defaultDecision": "DENY",
              "errorHandling": "ABSTAIN"
            },
            "documentCount": 12,
            "lastSuccessfulLoad": "2026-03-10T08:15:30Z"
          },
          "staging": {
            "state": "STALE",
            "configurationId": "v5",
            "combiningAlgorithm": {
              "votingMode": "PRIORITY_DENY",
              "defaultDecision": "DENY",
              "errorHandling": "PROPAGATE"
            },
            "documentCount": 3,
            "lastSuccessfulLoad": "2026-03-10T07:00:00Z",
            "lastFailedLoad": "2026-03-10T08:10:00Z",
            "lastError": "Parse error in staging-policy.sapl at line 5"
          }
        }
      }
    }
  }
}
```

Detail fields are only visible to authenticated users. The default configuration uses `show-details: when-authorized`. See [Security](../7_6_Security/) for securing actuator endpoints.

### Actuator Endpoints

| Endpoint | Auth Required | Description |
|----------|---------------|-------------|
| `/actuator/health` | No | Overall health status. Returns UP or DOWN for load balancers. |
| `/actuator/health/liveness` | No | Kubernetes liveness probe. Reports whether the JVM process is alive. |
| `/actuator/health/readiness` | No | Kubernetes readiness probe. Reports whether the node is ready to accept traffic. |
| `/actuator/info` | Yes | PDP configuration details: `configType`, `index`, `configPath`, `policiesPath`. |
| `/actuator/prometheus` | Yes | Prometheus metrics scrape endpoint. |

Health endpoints are unauthenticated so Kubernetes probes work without credentials. The info and prometheus endpoints require authentication to prevent information disclosure.

### Kubernetes Probes

Configure liveness, readiness, and startup probes for Kubernetes deployments:

```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: sapl
          image: ghcr.io/heutelbeck/sapl-node:4.0.0
          ports:
            - containerPort: 8443
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8443
            initialDelaySeconds: 15
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8443
            initialDelaySeconds: 10
            periodSeconds: 5
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8443
            initialDelaySeconds: 5
            periodSeconds: 5
            failureThreshold: 12
```

The startup probe gives the PDP time to compile policies before liveness checks begin. With the values above, the maximum startup time is 65 seconds (`initialDelaySeconds` + `periodSeconds` * `failureThreshold` = 5 + 5 * 12 = 65). Once the startup probe succeeds, Kubernetes switches to the liveness and readiness probes.

The liveness probe detects a hung JVM process. The readiness probe gates traffic until the node is ready. Both are independent of the PDP compilation state: a node that is still loading policies is alive but not yet ready.

### Decision Metrics

SAPL Node exposes four custom Prometheus metrics covering the golden signals for PDP decision traffic:

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `sapl.decisions` | Counter | `decision` (PERMIT, DENY, INDETERMINATE, NOT_APPLICABLE) | Total authorization decisions by outcome. |
| `sapl.decision.first.latency` | Timer | | Time from subscription to first decision. |
| `sapl.subscriptions.active` | Gauge | | Currently active SSE streaming subscriptions. |
| `sapl.subscription.duration` | Timer | | Total lifetime of completed subscriptions. |

These metrics cover both one shot (`decide-once`) and streaming (`decide`) endpoints. Standard Spring Boot HTTP metrics (`http.server.requests`) are also available for request level monitoring.

Enable metrics in `application.yml`:

```yaml
io.sapl.pdp.embedded:
  metrics-enabled: true
```

SAPL Node enables metrics by default. When embedding the PDP as a library, `metrics-enabled` defaults to `false`. When disabled, no metrics are recorded and there is zero runtime overhead. The property is a final boolean that the JIT compiler evaluates at startup. Dead metric recording branches are eliminated entirely.

Configure Prometheus to scrape the metrics endpoint:

```yaml
scrape_configs:
  - job_name: sapl
    metrics_path: /actuator/prometheus
    basic_auth:
      username: prometheus
      password: secret
    static_configs:
      - targets: ['sapl:8443']
```

The prometheus endpoint requires authentication. Use a dedicated service account with Basic Auth or API key credentials. See [Security](../7_6_Security/) for credential generation.

### Info Endpoint

The `/actuator/info` endpoint returns PDP configuration under the `sapl` key:

```json
{
  "sapl": {
    "configType": "BUNDLES",
    "index": "NAIVE",
    "configPath": "/policies",
    "policiesPath": "bundles"
  }
}
```

This endpoint requires authentication and is intended for operational dashboards and inventory systems.

### Decision Logging

The PDP emits structured JSON log entries via the reporting interceptor. Each entry contains the authorization subscription (subject, action, resource, environment), the decision (PERMIT, DENY, INDETERMINATE, NOT_APPLICABLE), and any obligations or advice attached to the decision.

Enable subscription lifecycle logging with two properties:

```yaml
io.sapl.pdp.embedded:
  print-subscription-events: true
  print-unsubscription-events: true
```

These log when a new authorization subscription starts and when it ends. This is useful for tracking active clients and debugging connection lifecycle issues.

Filtering, retention, and alerting on decision log entries are handled by your log infrastructure (Loki, ELK, Fluentd, CloudWatch). The PDP does not push logs to any external service.

### Evaluation Diagnostics

Four properties control diagnostic output during policy evaluation:

| Property | Description |
|----------|-------------|
| `print-trace` | Logs the full JSON evaluation trace on each decision. Shows every evaluation step the PDP performed. |
| `print-json-report` | Logs a JSON evaluation report on each decision. More compact than the full trace. |
| `print-text-report` | Logs a human readable text report on each decision. Shows which policies matched, how each was evaluated, and why the combining algorithm produced its result. |
| `pretty-print-reports` | Pretty prints JSON in logged traces and reports. |

Enable all diagnostics during development:

```yaml
io.sapl.pdp.embedded:
  print-trace: true
  print-json-report: true
  print-text-report: true
  pretty-print-reports: true
```

The text report is the most useful diagnostic tool for understanding why a particular decision was reached. It provides a step by step view of the evaluation process in a format designed for human consumption.

Disable all diagnostic properties in production. They produce significant log volume under load and are intended for development and staging environments only. See [Configuration](../7_2_Configuration/) for the full property reference.
