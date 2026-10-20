# ARO-Microservice-2 (`trade-service`)

**Azure Red Hat OpenShift (ARO) Trading Platform — Equities Order Execution Service**

`ARO-Microservice-2` is a Spring Boot 3.4 / Apache Camel 4.8 microservice designed for **Azure Red Hat OpenShift (ARO)**. It exposes an equities order execution API (`POST /api/v1/trades`) and uses the Apache Camel HTTP component to perform synchronous inter-service verification against [`ARO-Microservice-1` (`portfolio-service`)](../ARO-Microservice-1) before executing trades.

---

## 1. Directory Structure

```
ARO-Microservice-2/
├── Dockerfile                                # Red Hat UBI 9 OpenJDK 21 container build definition
├── README.md                                 # Service documentation
├── trade-service/                            # Maven project & Java 21 source code
│   ├── pom.xml                               # Spring Boot 3.4 & Apache Camel 4.8 dependencies
│   └── src/main/
│       ├── java/com/example/aro/trade/
│       │   ├── TradeServiceApplication.java  # Spring Boot entrypoint
│       │   ├── config/SecurityConfig.java    # HTTP Basic Auth & Actuator security configuration
│       │   ├── dto/                          # TradeRequest, TradeResponse & PortfolioPosition records
│       │   └── routes/TradeRouteBuilder.java # Camel REST DSL & HTTP producer route
│       └── resources/
│           ├── application.yml               # Externalized app, portfolio URL, TLS & Actuator config
│           └── logback-spring.xml            # Asynchronous JSON structured logging
└── deployment/                               # Declarative ARO manifests & observability
    ├── 01-namespace.yaml                     # OpenShift namespace (trade-app)
    ├── 02-secret-provider-class.yaml         # Azure Key Vault CSI SecretProviderClass (Managed Identity)
    ├── 03-configmap.yaml                     # Non-sensitive runtime ConfigMap
    ├── 04-trade-service.yaml                 # Deployment (3 replicas) & ClusterIP Service
    ├── 05-route.yaml                         # ARO TLS Edge Route
    ├── 06-servicemonitor.yaml                # Prometheus ServiceMonitor (/actuator/prometheus)
    └── grafana/
        └── trading-platform-dashboard.json   # Pre-built Grafana dashboard
```

---

## 2. Key Enterprise Capabilities

- **Runtime**: Java 21 LTS, Spring Boot 3.4.0, Apache Camel 4.8.0
- **Integration Pattern**: Synchronous REST ingestion (`platform-http`) → Apache Camel HTTP producer (`camel-http`) calling `portfolio-service` over internal OpenShift DNS (`http://portfolio-service:8080/api/v1/portfolios/{accountId}`)
- **Security**:
  - Spring Security HTTP Basic Authentication (`APP_SECURITY_USER` / `APP_SECURITY_PASSWORD`)
  - Zero-credential secret synchronization from **Azure Key Vault** using Azure Managed Identity ([`deployment/02-secret-provider-class.yaml`](deployment/02-secret-provider-class.yaml))
  - ARO Edge TLS Route termination ([`deployment/05-route.yaml`](deployment/05-route.yaml))
- **Observability**:
  - Structured JSON logs via `logstash-logback-encoder`
  - Micrometer + Prometheus metrics at `/actuator/prometheus` (route ID: `trade-execution-route`)
  - Readiness (`/actuator/health/readiness`) and Liveness (`/actuator/health/liveness`) probes

---

## 3. Build & Run Instructions

### 3.1 Compile the Maven Artifact
From the `ARO-Microservice-2` directory:

```bash
mvn clean package -DskipTests -f trade-service/pom.xml
```

### 3.2 Build the Container Image
The root [`Dockerfile`](Dockerfile) packages the compiled JAR onto `registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20`:

```bash
podman build -t trade-service:1.0.0 .
```

### 3.3 Deploy to Azure Red Hat OpenShift (ARO)
Apply the manifests in [`deployment/`](deployment):

```bash
oc apply -f deployment/01-namespace.yaml
oc apply -f deployment/02-secret-provider-class.yaml
oc apply -f deployment/03-configmap.yaml
oc apply -f deployment/04-trade-service.yaml
oc apply -f deployment/05-route.yaml
oc apply -f deployment/06-servicemonitor.yaml
```

### 3.4 Scale Instances (3 to 5 Replicas)
```bash
oc scale deployment/trade-service --replicas=5 -n trade-app
```

---

## 4. API Reference & Verification

### `POST /api/v1/trades`
Validates the account portfolio synchronously against `portfolio-service` and executes a `BUY` or `SELL` order.

```bash
TRADE_HOST=$(oc get route trade-service -n trade-app -o jsonpath='{.spec.host}')

curl -s -k -u "${APP_SECURITY_USER}:${APP_SECURITY_PASSWORD}" -X POST \
  "https://${TRADE_HOST}/api/v1/trades" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-1001",
    "symbol": "AAPL",
    "side": "BUY",
    "quantity": 25,
    "price": 225.50
  }' | jq .
```

**Sample Response (`200 OK`):**
```json
{
  "tradeId": "TRD-D7F3462F",
  "status": "EXECUTED",
  "accountId": "ACC-1001",
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 25,
  "price": 225.50,
  "totalValue": 5637.50,
  "portfolioStatus": "VERIFIED_AND_UPDATED",
  "timestamp": "2026-10-20T18:00:00.000Z"
}
```

---

## 5. Related Documentation

- [Root Repository Overview](../README.md)
- [Enterprise Architecture Guide](../docs/ARCHITECTURE.md)
- [Azure Key Vault Secrets Integration](../docs/AZURE-KEY-VAULT-SECRETS.md)
- [Observability & Grafana Dashboards](../docs/OBSERVABILITY-AND-DASHBOARDS.md)
