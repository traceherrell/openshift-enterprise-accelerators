# Container Adoption - On-Premises OpenShift (OCP) Deployment

This directory contains the declarative OpenShift manifests, Grafana dashboards, and architectural documentation for the **On-Premises Banking Platform** (`payment-service` and `transaction-service`).

---

## Directory Structure

```
deploy/on-prem/
├── 01-namespace.yaml                 # Application namespace (payment-app)
├── 02-secret-provider-class.yaml     # Azure Key Vault Secrets Store CSI Driver config
├── 02-secret.yaml.template           # Non-sensitive secret template
├── 03-configmap.yaml                 # Application ConfigMap
├── 04-payment-service.yaml           # Deployment (scalable 3-5) and Service
├── 05-transaction-service.yaml       # Deployment (scalable 3-5) and Service
├── 06-routes.yaml                    # HTTPS TLS Edge OpenShift Routes
├── 07-servicemonitor.yaml            # Prometheus User Workload Monitoring ServiceMonitor
└── grafana/                          # Grafana Dashboard Definitions
    └── banking-platform-dashboard.json # Pre-built dashboard for Prometheus metrics
```

The underlying microservice source code is located at the root of the repository:
- [`payment-service/`](../../payment-service)
- [`transaction-service/`](../../transaction-service)

Detailed documentation is consolidated in the root [`docs/`](../../docs) folder:
- [Enterprise Architecture & Banking Platform Design](../../docs/ARCHITECTURE.md#2-on-premises-architecture-banking-platform)
- [Developer Guide & Testing Instructions](../../docs/DEVELOPER-GUIDE.md)
- [Unified Azure Key Vault Secrets Procedure](../../docs/AZURE-KEY-VAULT-SECRETS.md)
- [HTTPS & Certificate Management](../../docs/CERTIFICATE-MANAGEMENT.md)
- [OpenTelemetry, Prometheus & Grafana Guide](../../docs/OBSERVABILITY-AND-DASHBOARDS.md)
