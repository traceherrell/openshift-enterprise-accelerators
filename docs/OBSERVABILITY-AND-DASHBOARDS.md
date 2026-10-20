# OpenTelemetry, Prometheus & Grafana Observability Guide

This document details the telemetry architecture, Prometheus metrics collection, and pre-built Grafana dashboards for the microservices across both **On-Premises OpenShift** and **Azure Red Hat OpenShift (ARO)**.

---

## 1. Observability Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        OpenShift Cluster / ARO                         │
│                                                                        │
│  ┌───────────────────────┐                  ┌───────────────────────┐  │
│  │    Banking Services   │                  │    Trading Services   │  │
│  │ - payment-service     │                  │ - trade-service       │  │
│  │ - transaction-service │                  │ - portfolio-service   │  │
│  │                       │                  │                       │  │
│  │ Spring Boot 3.4       │                  │ Spring Boot 3.4       │  │
│  │ Apache Camel 4.8      │                  │ Apache Camel 4.8      │  │
│  │ Micrometer + OTel     │                  │ Micrometer + OTel     │  │
│  │  /actuator/prometheus │                  │  /actuator/prometheus │  │
│  └───────────▲───────────┘                  └───────────▲───────────┘  │
│              │                                          │              │
│              └────────────────────┬─────────────────────┘              │
│                                   │ HTTPS Scrape (Every 15s)           │
│                      ┌────────────┴────────────┐                       │
│                      │ CoreOS ServiceMonitors  │                       │
│                      │ User Workload Monitoring│                       │
│                      └────────────┬────────────┘                       │
│                                   │ Remote Write / Pull Ingestion      │
│                                   ▼                                    │
│                      ┌─────────────────────────┐                       │
│                      │  Existing Prometheus DB │                       │
│                      └────────────┬────────────┘                       │
│                                   │ PromQL Queries                     │
└───────────────────────────────────┼────────────────────────────────────┘
                                    ▼
                    ┌───────────────────────────────┐
                    │       Grafana Instance        │
                    │   - Banking Platform Model    │
                    │   - Trading Platform Model    │
                    └───────────────────────────────┘
```

---

## 2. Telemetry Capabilities Implemented

Each microservice is instrumented with **Micrometer Prometheus Registry** and **OpenTelemetry** dependencies:

1. **Native Prometheus Exposition (`/actuator/prometheus`)**:
   - Endpoints are exposed beneath `/actuator/prometheus`.
   - Security: Whitelisted in `SecurityConfig.java` to permit scrape collectors without authentication credentials.
2. **Apache Camel Route Metrics (`camel-micrometer-starter`)**:
   - `camel_exchanges_total`: Total number of processed exchanges per route.
   - `camel_exchange_duration_seconds`: High-resolution timers measuring message transit times.
   - `camel_exchanges_failed_total`: Count of errored or dead-lettered exchanges.
3. **HTTP Server Metrics (Spring Boot Actuator)**:
   - `http_server_requests_seconds_count`: Total API request throughput by endpoint URI, HTTP method, and response status code.
   - `http_server_requests_seconds_bucket`: Percentiles and histogram buckets for P50, P95, and P99 latency SLA tracking.
4. **JVM & Container Resources**:
   - `jvm_memory_used_bytes` / `jvm_memory_max_bytes`: Heap and non-heap memory utilization.
   - `jvm_gc_pause_seconds_max`: Maximum garbage collection pause duration.
   - `process_cpu_usage` & `system_cpu_usage`: Real-time CPU utilization.
   - `jvm_threads_live_threads`: Active thread pool sizing.

---

## 3. CoreOS `ServiceMonitor` Declarations

OpenShift User Workload Monitoring automatically scrapes microservices using declarative CoreOS `ServiceMonitor` custom resources:

- **On-Premises**: [`On-Prem-Microservice-1/deployment/06-servicemonitor.yaml`](../On-Prem-Microservice-1/deployment/06-servicemonitor.yaml) & [`On-Prem-Microservice-2/deployment/06-servicemonitor.yaml`](../On-Prem-Microservice-2/deployment/06-servicemonitor.yaml)
- **ARO**: [`ARO-Microservice-1/deployment/06-servicemonitor.yaml`](../ARO-Microservice-1/deployment/06-servicemonitor.yaml) & [`ARO-Microservice-2/deployment/06-servicemonitor.yaml`](../ARO-Microservice-2/deployment/06-servicemonitor.yaml)
- **Local CRC**: [`deploy/local/08-servicemonitor.yaml`](../deploy/local/08-servicemonitor.yaml)

### Sample ServiceMonitor Manifest
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: payment-service-monitor
  namespace: payment-app
  labels:
    app.kubernetes.io/name: payment-service
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: payment-service
  endpoints:
    - port: https
      path: /actuator/prometheus
      interval: 15s
      scheme: http
```

---

## 4. Pre-Built Grafana Dashboards

Two production-ready Grafana dashboard definitions are included:

1. **Banking Platform Dashboard**:
   - Location: [`On-Prem-Microservice-1/deployment/grafana/banking-platform-dashboard.json`](../On-Prem-Microservice-1/deployment/grafana/banking-platform-dashboard.json)
   - Visualizes: Payment ingestion rate, Kafka settlement throughput, Camel consumer latency, JVM memory pools, and HTTP 5xx error rates.
2. **Trading Platform Dashboard**:
   - Location: [`ARO-Microservice-1/deployment/grafana/trading-platform-dashboard.json`](../ARO-Microservice-1/deployment/grafana/trading-platform-dashboard.json)
   - Visualizes: Equity trade execution rate, inter-service HTTP call latency to `portfolio-service`, portfolio read volume, and JVM GC pauses.

---

## 5. Grafana Import Instructions

To import dashboards into your existing Grafana environment:

1. **Log In to Grafana**: Navigate to your enterprise Grafana web console.
2. **Verify Prometheus DataSource**: Ensure a Prometheus datasource is configured (default name: `Prometheus`).
3. **Import Dashboard JSON**:
   - In Grafana, click the **+** (Create) icon in the sidebar and select **Import**.
   - Click **Upload JSON file** and select either:
     - `On-Prem-Microservice-1/deployment/grafana/banking-platform-dashboard.json`
     - `ARO-Microservice-1/deployment/grafana/trading-platform-dashboard.json`
   - Select your Prometheus datasource from the dropdown.
   - Click **Import**.

---

## 6. PromQL Query Reference

| Metric | PromQL Expression | Purpose |
| :--- | :--- | :--- |
| **Payment Throughput** | `sum(rate(camel_exchanges_total{routeId="payment-process-route"}[1m]))` | Real-time payment ingestion rate per second |
| **Kafka Settlement Rate**| `sum(rate(camel_exchanges_total{routeId="amq-transaction-consumer-route"}[1m]))` | Transactions settled per second from Kafka |
| **Trade Execution Rate** | `sum(rate(camel_exchanges_total{routeId="trade-execution-route"}[1m]))` | Equity trades executed per second |
| **Inter-Service Latency**| `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri=~"/api/v1/portfolios.*"}[5m])) by (le))` | P95 latency of synchronous portfolio lookups |
| **JVM Heap Usage** | `sum(jvm_memory_used_bytes{area="heap"}) by (application)` | Total heap consumption per microservice |
| **Error Rate (5xx)** | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m]))` | HTTP 5xx server-side fault frequency |
