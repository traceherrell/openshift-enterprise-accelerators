# Developer Guide & Template Reference

This guide provides step-by-step instructions for developers to build, test, deploy, scale, and reuse the reference microservices across both **On-Premises OpenShift** and **Azure Red Hat OpenShift (ARO)**.

---

## 1. Project Prerequisites

Ensure your local development workstation is equipped with:
- **Java Development Kit (JDK)**: OpenJDK 21 LTS
- **Apache Maven**: Version 3.9+ (or use the Maven Wrapper `./mvnw` within service folders)
- **OpenShift CLI (`oc`)**: Version 4.14+ (or OpenShift Local `crc`)
- **Azure CLI (`az`)**: Version 2.60+ (for reading Azure Key Vault secrets during local testing)
- **cURL & jq**: For invoking and formatting REST API responses

---

## 2. Codebase Structure

The repository separates the four autonomous microservices at the root directory from environment manifests consolidated under `deploy/`:

```
├── payment-service/          # Banking: Payment REST Ingestion & Kafka Producer
├── transaction-service/      # Banking: AMQ Streams Kafka Settlement Consumer
├── trade-service/            # Trading: Equities Order REST & Inter-Service Producer
├── portfolio-service/        # Trading: Portfolio & Positions REST Provider
│
├── deploy/                   # Declarative Deployment Manifests
│   ├── local/                # OpenShift Local (CRC) automation & ephemeral manifests
│   ├── on-prem/              # On-Premises OCP manifests & Grafana dashboard
│   └── azure/                # Azure Red Hat OpenShift (ARO) manifests & Grafana dashboard
│
├── azure-keyvault/           # Azure Key Vault Unified Secrets Automation
│   ├── setup-azure-keyvault.sh # Zero-credential provisioning script
│   └── README.md             # Multi-cloud secrets architecture guide
│
├── docs/                     # Authoritative Central Documentation Hub
│   ├── ARCHITECTURE.md       # Full architecture, component flows, & diagrams
│   ├── DEVELOPER-GUIDE.md    # Developer build, test, scaling, & template guide
│   ├── AZURE-KEY-VAULT-SECRETS.md # Unified Azure Key Vault secrets procedure
│   ├── CERTIFICATE-MANAGEMENT.md  # HTTPS, Ingress TLS, & certificate rotation
│   ├── OBSERVABILITY-AND-DASHBOARDS.md # OpenTelemetry, Prometheus & Grafana
│   └── LOCAL-CRC-DEPLOYMENT.md    # Local CRC setup and developer verification
│
└── README.md
```

---

## 3. Local Build & Execution

### 3.1 Compiling Microservice Packages
From the repository root, compile all microservices or run individually:

```bash
# Compile all microservices
mvn clean package -DskipTests -f payment-service/pom.xml
mvn clean package -DskipTests -f transaction-service/pom.xml
mvn clean package -DskipTests -f trade-service/pom.xml
mvn clean package -DskipTests -f portfolio-service/pom.xml
```

### 3.2 Running Locally with Azure Key Vault Credentials
You can pull runtime credentials directly from Azure Key Vault into your terminal session:

```bash
# Retrieve credentials from Azure Key Vault
KEYVAULT_NAME="${AZURE_KEYVAULT_NAME:-kv-enterprise-secrets}"

export APP_SECURITY_USER=$(az keyvault secret show --vault-name "$KEYVAULT_NAME" --name "payment-api-user" --query value -o tsv)
export APP_SECURITY_PASSWORD=$(az keyvault secret show --vault-name "$KEYVAULT_NAME" --name "payment-api-password" --query value -o tsv)

# Run Payment Service locally
cd payment-service
mvn spring-boot:run
```

---

## 4. Testing Endpoints & API Workflows

### 4.1 Automated Test Execution
If running on OpenShift Local (CRC) or an active cluster, execute the automated test runner:

```bash
./deploy/local/test-local.sh
```

### 4.2 Manual cURL Verification Commands

> [!TIP]
> Always wrap passwords containing special characters (like `!`) in **single quotes** (`'...'`) in bash/zsh to prevent history expansion issues.

#### Test 1: Query Portfolio Positions (Portfolio Service)
```bash
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' \
  https://portfolio-service-trade-app.apps-crc.testing/api/v1/portfolios/ACC-1001 | jq .
```
**Sample Output:**
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

#### Test 2: Execute Trade Order (Trade Service -> Synchronous Camel HTTP Call)
```bash
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' -X POST \
  https://trade-service-trade-app.apps-crc.testing/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{"accountId":"ACC-1001","symbol":"AAPL","side":"BUY","quantity":25,"price":225.50}' | jq .
```
**Sample Output:**
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
  "timestamp": "2026-10-14T22:53:21.534Z"
}
```

#### Test 3: Ingest Payment into Kafka (Payment Service)
```bash
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' -X POST \
  https://payment-service-payment-app.apps-crc.testing/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"PAY-1001","sourceAccount":"ACC-1001","destinationAccount":"ACC-2002","amount":2500.00,"currency":"USD","description":"Invoice"}' | jq .
```
**Sample Output:**
```json
{
  "paymentId": "PAY-1001",
  "status": "ACCEPTED",
  "message": "Payment accepted for processing and published to AMQ Streams",
  "amount": 2500.00,
  "currency": "USD",
  "timestamp": "2026-10-14T22:55:22.980Z"
}
```

#### Test 4: Verify Kafka Consumer Settlement Logs (Transaction Service)
```bash
oc logs deployment/transaction-service -n payment-app --tail=10
```
**Sample Output:**
```json
{"@timestamp":"2026-10-14T22:55:22.986Z","message":"Received payment event from AMQ Streams topic [payments.v1]: {\"paymentId\":\"PAY-1001\"...}","logger_name":"amq-transaction-consumer-route","level":"INFO"}
{"@timestamp":"2026-10-14T22:55:23.043Z","message":"Transaction settled: TX_ID=TX-92772515, Payment_ID=PAY-1001, Amount=2500.00 USD","logger_name":"com.example.onprem.transaction.routes.TransactionRouteBuilder","level":"INFO"}
```

#### Test 5: Scrape Prometheus Metrics Endpoint
```bash
curl -s -k https://payment-service-payment-app.apps-crc.testing/actuator/prometheus | grep -E "camel_exchanges_total"
```

---

## 5. Scaling Microservices (3–5 Instances)

Both On-Premises and ARO deployments are designed for manual and automated scaling:

### Scaling On-Premises Banking Services
```bash
# Scale Payment Service to 3 instances
oc scale deployment/payment-service --replicas=3 -n payment-app

# Scale Transaction Service to 3 instances
oc scale deployment/transaction-service --replicas=3 -n payment-app

# Verify replicas are Running
oc get pods -n payment-app -l app.kubernetes.io/name=payment-service
```

### Scaling ARO Trading Services
```bash
# Scale Trade Service to 5 instances
oc scale deployment/trade-service --replicas=5 -n trade-app

# Scale Portfolio Service to 3 instances
oc scale deployment/portfolio-service --replicas=3 -n trade-app

# Verify replicas
oc get pods -n trade-app
```

---

## 6. How to Reuse These Microservices as Templates

Follow these steps to create a new microservice based on this reference architecture:

1. **Clone an Existing Service Directory**:
   ```bash
   cp -r payment-service settlement-service
   cd settlement-service
   ```
2. **Update Maven Identifiers in `pom.xml`**:
   Update `<artifactId>` to `settlement-service` and `<name>` to `Settlement Service`.
3. **Customize Application Configuration (`application.yml`)**:
   Update `spring.application.name` and any domain-specific properties:
   ```yaml
   spring:
     application:
       name: settlement-service
   ```
4. **Implement Custom Apache Camel Routes**:
   Create a new route class extending `RouteBuilder` under `com.example...routes`:
   ```java
   @Component
   public class SettlementRouteBuilder extends RouteBuilder {
       @Override
       public void configure() throws Exception {
           rest("/v1/settlements")
               .post()
               .to("direct:processSettlement");

           from("direct:processSettlement")
               .routeId("settlement-route")
               .log("Settling transaction: ${body}");
       }
   }
   ```
5. **Add OpenShift Deployment Manifest**:
   Copy [`deploy/on-prem/04-payment-service.yaml`](../deploy/on-prem/04-payment-service.yaml), update names and labels, and reference the shared Azure Key Vault `SecretProviderClass`.
