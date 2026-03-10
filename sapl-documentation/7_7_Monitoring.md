---
layout: default
title: Monitoring
parent: SAPL Node
nav_order: 707
---

## Monitoring and Observability

SAPL Node exposes health, metrics, and decision data through standard Spring Boot Actuator and Micrometer interfaces. There is no proprietary monitoring agent or cloud service -- use your existing observability stack (Prometheus, Grafana, Loki, ELK, or any tool that consumes these standard interfaces).

### Planned Topics

- **PDP health indicator:** The three PDP states (LOADED, STALE, ERROR) and what each means operationally. LOADED = policies compiled and active (UP). STALE = a hot-reload failed but the previous working configuration is still serving decisions (UP with warning detail). ERROR = no valid configuration could be loaded (DOWN). Health details include configuration ID, combining algorithm, document count, and last-load timestamps. Detail visibility requires authentication (`show-details: when-authorized`). Multi-tenant health: all LOADED = UP, any STALE = UP with warning, any ERROR = DOWN.
- **Kubernetes probes:** Liveness, readiness, and startup probe configuration. Liveness and readiness are independent of PDP compilation state (the JVM process is alive even while policies are loading). The startup probe gives the PDP time to compile policies before liveness checks begin. Annotated K8s deployment YAML example. Migrate from sapl-node README.
- **Decision metrics:** The four custom Prometheus metrics covering the golden signals for PDP traffic: `sapl.decisions` (counter by outcome), `sapl.decision.first.latency` (timer), `sapl.subscriptions.active` (gauge), `sapl.subscription.duration` (timer). What each metric tells you operationally. Enabling via `io.sapl.pdp.embedded.metrics-enabled`. Zero overhead when disabled. Prometheus scrape configuration example. Migrate from sapl-node README.
- **Decision logging:** SAPL does not push decision logs to a proprietary service. Instead, the PDP emits structured JSON log entries via the reporting interceptor. These contain the authorization subscription (subject, action, resource, environment), the decision (PERMIT/DENY/INDETERMINATE/NOT_APPLICABLE), and any obligations or advice. Standard log aggregation tools (Loki, ELK, Fluentd, CloudWatch) collect, index, and retain these logs. Configuring the log level and format for decision entries. Filtering and retention are handled by your log infrastructure, not by the PDP.
- **Evaluation diagnostics (text report mode):** Enabling `io.sapl.pdp.embedded.print-text-report: true` for human-readable evaluation traces during development. Shows which policies matched, how each was evaluated, and why the combining algorithm produced its result. Intended for development and staging -- disable in production due to log volume. This is the equivalent of a debug mode for policy evaluation.
- **Actuator endpoints:** Summary table of exposed endpoints (/actuator/health, /actuator/health/liveness, /actuator/health/readiness, /actuator/info, /actuator/prometheus), which require authentication, and what each returns. Migrate from sapl-node README.

> **Planned content.** Actuator endpoints, K8s probes, and Prometheus metrics will be migrated from the `sapl-node` README. Health state semantics, decision logging guidance, and evaluation diagnostics are new content.
