# ARO-Microservice-1 (`portfolio-service`)

**Azure Red Hat OpenShift (ARO) Trading Platform — Portfolio & Positions Service**

`ARO-Microservice-1` is a Spring Boot 3.4 / Apache Camel 4.8 microservice designed for **Azure Red Hat OpenShift (ARO)**. It manages institutional equity holdings and cash balances, exposing a secure REST API (`GET /api/v1/portfolios/{accountId}`) invoked both directly by clients and synchronously by [`ARO-Microservice-2` (`trade-service`)](../ARO-Microservice-2) during trade order execution.

---

## 1. Directory Structure

```
ARO-Microservice-1/
├── Dockerfile                                    # Red Hat UBI 9 OpenJDK 21 container build definition
├── README.md                                     # Service documentation
├── pom.xml                                       # Spring Boot 3.4.13 & Apache Camel 4.8.5 dependencies
├── src/main/
│   ├── java/com/example/aro/portfolio/
│   │   ├── PortfolioServiceApplication.java      # Spring Boot entrypoint
│   │   ├── config/SecurityConfig.java            # HTTP Basic Auth & Actuator security configuration
│   │   ├── dto/PortfolioPosition.java            # Portfolio holdings & cash balance record
│   │   └── routes/PortfolioRouteBuilder.java     # Camel REST DSL portfolio lookup route
│   └── resources/
│       ├── application.yml                       # Externalized app, TLS & Actuator config
│       └── logback-spring.xml                    # Asynchronous JSON structured logging
└── deployment/                                   # Declarative ARO manifests & observability
    ├── 01-namespace.yaml                         # OpenShift namespace (trade-app)
    ├── 02-secret-provider-class.yaml             # Azure Key Vault CSI SecretProviderClass (Managed Identity)
    ├── 03-configmap.yaml                         # Non-sensitive runtime ConfigMap
    ├── 04-portfolio-service.yaml                 # Deployment (3 replicas) & ClusterIP Service
    ├── 05-route.yaml                             # ARO TLS Edge Route
    ├── 06-servicemonitor.yaml                    # Prometheus ServiceMonitor (/actuator/prometheus)
    └── grafana/
        └── trading-platform-dashboard.json       # Pre-built Grafana dashboard
```

---

## 2. Key Enterprise Capabilities

- **Runtime**: Java 21 LTS, Spring Boot 3.4.0, Apache Camel 4.8.0
- **Integration Pattern**: Synchronous REST provider (`platform-http`) serving institutional account holdings and cash balances
- **Security**:
  - Spring Security HTTP Basic Authentication (`APP_SECURITY_USER` / `APP_SECURITY_PASSWORD`)
  - Zero-credential secret synchronization from **Azure Key Vault** using Azure Managed Identity ([`deployment/02-secret-provider-class.yaml`](deployment/02-secret-provider-class.yaml))
  - ARO Edge TLS Route termination ([`deployment/05-route.yaml`](deployment/05-route.yaml))
- **Observability**:
  - Structured JSON logs via `logstash-logback-encoder`
  - Micrometer + Prometheus metrics at `/actuator/prometheus` (route ID: `portfolio-lookup-route`)
  - Readiness (`/actuator/health/readiness`) and Liveness (`/actuator/health/liveness`) probes

---

## 3. Build & Run Instructions

### 3.1 Compile the Maven Artifact
From the `ARO-Microservice-1` directory:

```bash
mvn clean package -DskipTests
```

### 3.2 Build the Container Image
The root [`Dockerfile`](Dockerfile) packages the compiled JAR onto `registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20`:

```bash
podman build -t portfolio-service:1.0.0 .
```

### 3.3 Deploy to Azure Red Hat OpenShift (ARO)
Apply the manifests in [`deployment/`](deployment):

```bash
oc apply -f deployment/01-namespace.yaml
oc apply -f deployment/02-secret-provider-class.yaml
oc apply -f deployment/03-configmap.yaml
oc apply -f deployment/04-portfolio-service.yaml
oc apply -f deployment/05-route.yaml
oc apply -f deployment/06-servicemonitor.yaml
```

### 3.4 Scale Instances (3 to 5 Replicas)
```bash
oc scale deployment/portfolio-service --replicas=5 -n trade-app
```

---

## 4. API Reference & Verification

### `GET /api/v1/portfolios/{accountId}`
Returns the current equity holdings and cash balance for an institutional account (e.g., `ACC-1001`).

```bash
PORTFOLIO_HOST=$(oc get route portfolio-service -n trade-app -o jsonpath='{.spec.host}')

curl -s -k -u "${APP_SECURITY_USER}:${APP_SECURITY_PASSWORD}" \
  "https://${PORTFOLIO_HOST}/api/v1/portfolios/ACC-1001" | jq .
```

**Sample Response (`200 OK`):**
```json
{
  "accountId": "ACC-1001",
  "cashBalance": 500000.00,
  "currency": "USD",
  "holdings": {
    "MSFT": 300,
    "NVDA": 150,
    "AAPL": 500
  },
  "status": "ACTIVE"
}
```

---

## 5. Related Documentation

- [Root Repository Overview](../README.md)
- [Enterprise Architecture Guide](../docs/ARCHITECTURE.md)
- [Azure Key Vault Secrets Integration](../docs/AZURE-KEY-VAULT-SECRETS.md)
- [Observability & Grafana Dashboards](../docs/OBSERVABILITY-AND-DASHBOARDS.md)
