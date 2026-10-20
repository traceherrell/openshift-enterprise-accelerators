# Cloud & Container Adoption - Reference Implementations

This repository delivers production-grade reference implementations, containerization, declarative OpenShift manifests, unified dynamic secrets management with **Azure Key Vault**, and architectural documentation for enterprise container adoption.

---

## Workspace Structure

Each microservice is structured in its own self-contained project folder containing its root `Dockerfile`, source code directory, and `deployment/` manifests:

```
├── On-Prem-Microservice-1/   # On-Premises Microservice 1 (Payment Ingestion & Producer)
│   ├── Dockerfile            # Root UBI 9 OpenJDK 21 container build definition
│   ├── payment-service/      # Spring Boot 3.4 & Apache Camel 4.8 Maven project
│   └── deployment/           # OpenShift manifests, Route, ServiceMonitor & Grafana
│
├── On-Prem-Microservice-2/   # On-Premises Microservice 2 (Kafka Settlement Consumer)
│   ├── Dockerfile            # Root UBI 9 OpenJDK 21 container build definition
│   ├── transaction-service/  # Spring Boot 3.4 & Apache Camel 4.8 Maven project
│   └── deployment/           # OpenShift manifests, Route, ServiceMonitor & Grafana
│
├── ARO-Microservice-1/       # ARO Microservice 1 (Portfolio & Holdings Provider)
│   ├── Dockerfile            # Root UBI 9 OpenJDK 21 container build definition
│   ├── portfolio-service/    # Spring Boot 3.4 & Apache Camel 4.8 Maven project
│   └── deployment/           # ARO manifests, Route, ServiceMonitor & Grafana
│
├── ARO-Microservice-2/       # ARO Microservice 2 (Equities Order & Camel HTTP Producer)
│   ├── Dockerfile            # Root UBI 9 OpenJDK 21 container build definition
│   ├── trade-service/        # Spring Boot 3.4 & Apache Camel 4.8 Maven project
│   └── deployment/           # ARO manifests, Route, ServiceMonitor & Grafana
│
├── deploy/
│   └── local/                # Full-stack OpenShift Local (CRC) manifests & automation scripts
│
├── azure-keyvault/           # Azure Key Vault Unified Secrets Automation
│   ├── setup-azure-keyvault.sh # Automated Azure Key Vault, RBAC, and secrets provisioning
│   └── README.md             # Unified secrets guide for On-Prem OCP & ARO
│
├── docs/                     # Centralized Authoritative Documentation Hub
│   ├── ARCHITECTURE.md       # Multi-platform architecture, flows, & comparison
│   ├── DEVELOPER-GUIDE.md    # Developer build, test, scaling, & template guide
│   ├── AZURE-KEY-VAULT-SECRETS.md # Unified Azure Key Vault secrets procedure
│   ├── CERTIFICATE-MANAGEMENT.md  # HTTPS, Ingress TLS, & certificate rotation
│   ├── OBSERVABILITY-AND-DASHBOARDS.md # OpenTelemetry, Prometheus, & Grafana
│   └── LOCAL-CRC-DEPLOYMENT.md    # Self-contained CRC local deployment guide
│
├── .gitignore                # Excludes all keystores, certs, .env, and sensitive files
└── README.md
```

---

## Architectural Comparison Matrix

| Capability | On-Premises Implementation (`On-Prem-Microservice-1` & `2`) | ARO Implementation (`ARO-Microservice-1` & `2`) |
| :--- | :--- | :--- |
| **Domain** | Banking: Payments & Transaction Settlement | Banking: Equities Trade & Portfolio Management |
| **Microservices** | [`payment-service`](On-Prem-Microservice-1/payment-service) & [`transaction-service`](On-Prem-Microservice-2/transaction-service) | [`portfolio-service`](ARO-Microservice-1/portfolio-service) & [`trade-service`](ARO-Microservice-2/trade-service) |
| **Runtime & Framework** | Java 21, Spring Boot 3.4.0, Apache Camel 4.8.0 DSL | Java 21, Spring Boot 3.4.0, Apache Camel 4.8.0 DSL |
| **Interconnection** | Event-Driven via AMQ Streams (Kafka topic `payments.v1`) | Synchronous REST via Apache Camel HTTP component |
| **REST Security** | Spring Security HTTP Basic Auth (secret-driven) | Spring Security HTTP Basic Auth (secret-driven) |
| **HTTPS / TLS** | OpenShift Edge TLS Route & Container TLS | ARO Ingress Edge TLS Route & Container TLS |
| **Secrets Engine** | **Azure Key Vault** via Secrets Store CSI Driver (Service Principal / Workload Identity) | **Azure Key Vault** via Secrets Store CSI Driver (User-Assigned Managed Identity) |
| **Logging** | Async Logback JSON (stdout & optional TCP/SSL) | Async Logback JSON (stdout & optional TCP/SSL) |
| **Telemetry & Observability** | OpenTelemetry / Prometheus (`/actuator/prometheus`) + Grafana Dashboards | OpenTelemetry / Prometheus (`/actuator/prometheus`) + Grafana Dashboards |
| **Scaling** | Manual scaling (3–5 replicas configured) | Manual scaling (3–5 replicas configured) |
| **Container Base** | Red Hat UBI 9 OpenJDK 21 Runtime | Red Hat UBI 9 OpenJDK 21 Runtime |

---

## Zero-Credential Policy (Public Git Ready)

This repository is strictly designed for safe public GitHub / GitLab hosting:
1. **Zero Hardcoded Passwords**: No passwords, API keys, or certificates are present in source code or YAML manifests.
2. **Environment & CSI Injected**: All credentials are dynamically resolved at runtime via environment variables (`APP_SECURITY_USER`, `APP_SECURITY_PASSWORD`) synchronized by the Secrets Store CSI Driver.
3. **Automated Key Vault Provisioning**: Run [`azure-keyvault/setup-azure-keyvault.sh`](azure-keyvault/setup-azure-keyvault.sh) to initialize Azure Key Vault and generate cryptographically secure secrets.
4. **Unified Cloud & On-Prem Secrets**: Applications across both environments share a unified pattern with Azure Key Vault, eliminating the need to manage disparate secret backends.

---

## Documentation Index

The central [`docs/`](docs) folder provides consolidated, multi-platform documentation:

- **Architecture & System Design**:
  - [Enterprise Architecture, Component Flows & Diagrams](docs/ARCHITECTURE.md)
- **Developer & Operations Guides**:
  - [Developer Guide, Testing, Scaling & Template Reference](docs/DEVELOPER-GUIDE.md)
  - [OpenShift Local (CRC) Deployment & Verification Guide](docs/LOCAL-CRC-DEPLOYMENT.md)
- **Security & Secrets**:
  - [Unified Azure Key Vault Secrets Management Guide](docs/AZURE-KEY-VAULT-SECRETS.md)
  - [HTTPS, Edge Routes & Certificate Management](docs/CERTIFICATE-MANAGEMENT.md)
- **Observability & Dashboards**:
  - [OpenTelemetry, Prometheus & Grafana Guide](docs/OBSERVABILITY-AND-DASHBOARDS.md)
- **Automation Scripts**:
  - [Azure Key Vault Provisioning Script](azure-keyvault/setup-azure-keyvault.sh)
  - [Local CRC Deployment Script](deploy/local/deploy-local.sh)
  - [Local CRC Automated Test Runner](deploy/local/test-local.sh)
