# Azure Red Hat OpenShift (ARO) Deployment

This directory contains the declarative ARO manifests, Grafana dashboards, and architectural documentation for the **Trading Platform** (`trade-service` and `portfolio-service`).

---

## Directory Structure

```
deploy/azure/
├── 01-namespace.yaml                 # Application namespace (trade-app)
├── 02-secret-provider-class.yaml     # Azure Key Vault Secrets Store CSI Driver config
├── 03-configmap.yaml                 # Application ConfigMap
├── 04-trade-service.yaml             # Deployment (scalable 3-5) and Service
├── 05-portfolio-service.yaml         # Deployment (scalable 3-5) and Service
├── 06-routes.yaml                    # HTTPS TLS Edge OpenShift Routes
├── 07-servicemonitor.yaml            # Prometheus User Workload Monitoring ServiceMonitor
└── grafana/                          # Grafana Dashboard Definitions
    └── trading-platform-dashboard.json # Pre-built dashboard for Prometheus metrics
```

The underlying microservice source code is located at the root of the repository:
- [`trade-service/`](../../trade-service)
- [`portfolio-service/`](../../portfolio-service)

Detailed documentation is consolidated in the root [`docs/`](../../docs) folder:
- [Enterprise Architecture & Trading Platform Design](../../docs/ARCHITECTURE.md#3-azure-red-hat-openshift-aro-architecture-trading-platform)
- [Developer Guide & Testing Instructions](../../docs/DEVELOPER-GUIDE.md)
- [Unified Azure Key Vault Secrets Procedure](../../docs/AZURE-KEY-VAULT-SECRETS.md)
- [HTTPS & Certificate Management](../../docs/CERTIFICATE-MANAGEMENT.md)
- [OpenTelemetry, Prometheus & Grafana Guide](../../docs/OBSERVABILITY-AND-DASHBOARDS.md)
