# On-Prem-Microservice-1 (`payment-service`)

**On-Premises Banking Platform — Payment Ingestion & AMQ Streams (Kafka) Producer**

`On-Prem-Microservice-1` is a Spring Boot 3.4 / Apache Camel 4.8 microservice designed for **On-Premises Red Hat OpenShift Container Platform (OCP)**. It exposes a secure HTTPS REST API (`POST /api/v1/payments`), validates incoming payment requests, and publishes asynchronous payment events to Red Hat AMQ Streams (Apache Kafka) topic `payments.v1` for downstream settlement by [`On-Prem-Microservice-2`](../On-Prem-Microservice-2).

---

## 1. Directory Structure

```
On-Prem-Microservice-1/
├── Dockerfile                                  # Red Hat UBI 9 OpenJDK 21 container build definition
├── README.md                                   # Service documentation
├── pom.xml                                     # Spring Boot 3.4.13 & Apache Camel 4.8.5 dependencies
├── src/main/
│   ├── java/com/example/onprem/payment/
│   │   ├── PaymentServiceApplication.java      # Spring Boot entrypoint
│   │   ├── config/SecurityConfig.java          # HTTP Basic Auth & Actuator security configuration
│   │   ├── dto/                                # PaymentRequest & PaymentResponse records
│   │   └── routes/PaymentRouteBuilder.java     # Camel REST DSL & Kafka producer route
│   └── resources/
│       ├── application.yml                     # Externalized app, Kafka, TLS & Actuator config
│       └── logback-spring.xml                  # Asynchronous JSON structured logging
└── deployment/                                 # Declarative OpenShift manifests & observability
    ├── 01-namespace.yaml                       # OpenShift namespace (payment-app)
    ├── 02-secret-provider-class.yaml           # Azure Key Vault CSI SecretProviderClass
    ├── 02-secret.yaml.template                 # Fallback Secret template
    ├── 03-configmap.yaml                       # Non-sensitive runtime ConfigMap
    ├── 04-payment-service.yaml                 # Deployment (3 replicas) & ClusterIP Service
    ├── 05-route.yaml                           # OpenShift TLS Edge Route
    ├── 06-servicemonitor.yaml                  # Prometheus ServiceMonitor (/actuator/prometheus)
    └── grafana/
        └── banking-platform-dashboard.json     # Pre-built Grafana dashboard
```

---

## 2. Key Enterprise Capabilities

- **Runtime**: Java 21 LTS, Spring Boot 3.4.0, Apache Camel 4.8.0
- **Integration Pattern**: REST API ingestion (`platform-http`) → Asynchronous Kafka producer (`camel-kafka`) to topic `payments.v1`
- **Security**:
  - Spring Security HTTP Basic Authentication (`APP_SECURITY_USER` / `APP_SECURITY_PASSWORD`)
  - Secrets injected dynamically from **Azure Key Vault** via the Secrets Store CSI Driver ([`deployment/02-secret-provider-class.yaml`](deployment/02-secret-provider-class.yaml))
  - OpenShift Edge TLS Route termination ([`deployment/05-route.yaml`](deployment/05-route.yaml))
- **Observability**:
  - Structured JSON logs via `logstash-logback-encoder`
  - Micrometer + Prometheus metrics at `/actuator/prometheus`
  - Readiness (`/actuator/health/readiness`) and Liveness (`/actuator/health/liveness`) probes

---

## 3. Build & Run Instructions

### 3.1 Compile the Maven Artifact
From the `On-Prem-Microservice-1` directory:

```bash
mvn clean package -DskipTests
```

### 3.2 Build the Container Image
The root [`Dockerfile`](Dockerfile) packages the compiled JAR onto `registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20`:

```bash
podman build -t payment-service:1.0.0 .
```

### 3.3 Deploy to On-Premises OpenShift
Apply the manifests in [`deployment/`](deployment):

```bash
oc apply -f deployment/01-namespace.yaml
oc apply -f deployment/02-secret-provider-class.yaml
oc apply -f deployment/03-configmap.yaml
oc apply -f deployment/04-payment-service.yaml
oc apply -f deployment/05-route.yaml
oc apply -f deployment/06-servicemonitor.yaml
```

### 3.4 Scale Instances (3 to 5 Replicas)
```bash
oc scale deployment/payment-service --replicas=5 -n payment-app
```

---

## 4. API Reference & Verification

### `POST /api/v1/payments`
Submits a payment instruction for asynchronous publishing to AMQ Streams (`payments.v1`).

```bash
PAYMENT_HOST=$(oc get route payment-service -n payment-app -o jsonpath='{.spec.host}')

curl -s -k -u "${APP_SECURITY_USER}:${APP_SECURITY_PASSWORD}" -X POST \
  "https://${PAYMENT_HOST}/api/v1/payments" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "PAY-1001",
    "sourceAccount": "ACC-1001",
    "destinationAccount": "ACC-2002",
    "amount": 2500.00,
    "currency": "USD",
    "description": "Enterprise Wire Transfer"
  }' | jq .
```

**Sample Response (`202 Accepted`):**
```json
{
  "paymentId": "PAY-1001",
  "status": "ACCEPTED",
  "message": "Payment accepted for processing and published to AMQ Streams",
  "amount": 2500.00,
  "currency": "USD",
  "timestamp": "2026-10-20T18:00:00.000Z"
}
```

---

## 5. Related Documentation

- [Root Repository Overview](../README.md)
- [Enterprise Architecture Guide](../docs/ARCHITECTURE.md)
- [Azure Key Vault Secrets Integration](../docs/AZURE-KEY-VAULT-SECRETS.md)
- [Observability & Grafana Dashboards](../docs/OBSERVABILITY-AND-DASHBOARDS.md)
