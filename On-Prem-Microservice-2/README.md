# On-Prem-Microservice-2 (`transaction-service`)

**On-Premises Banking Platform — AMQ Streams (Kafka) Settlement Consumer**

`On-Prem-Microservice-2` is a Spring Boot 3.4 / Apache Camel 4.8 microservice designed for **On-Premises Red Hat OpenShift Container Platform (OCP)**. It subscribes to Red Hat AMQ Streams (Apache Kafka) topic `payments.v1` using consumer group `transaction-processing-group`, processes and settles payment events published by [`On-Prem-Microservice-1`](../On-Prem-Microservice-1), and exposes a settlement status REST endpoint (`GET /api/v1/transactions/status`).

---

## 1. Directory Structure

```
On-Prem-Microservice-2/
├── Dockerfile                                      # Red Hat UBI 9 OpenJDK 21 container build definition
├── README.md                                       # Service documentation
├── transaction-service/                            # Maven project & Java 21 source code
│   ├── pom.xml                                     # Spring Boot 3.4 & Apache Camel 4.8 dependencies
│   └── src/main/
│       ├── java/com/example/onprem/transaction/
│       │   ├── TransactionServiceApplication.java  # Spring Boot entrypoint
│       │   ├── config/SecurityConfig.java          # HTTP Basic Auth & Actuator security configuration
│       │   ├── dto/                                # PaymentEvent & TransactionRecord records
│       │   └── routes/TransactionRouteBuilder.java # Camel Kafka consumer & settlement route
│       └── resources/
│           ├── application.yml                     # Externalized app, Kafka, TLS & Actuator config
│           └── logback-spring.xml                  # Asynchronous JSON structured logging
└── deployment/                                     # Declarative OpenShift manifests & observability
    ├── 01-namespace.yaml                           # OpenShift namespace (payment-app)
    ├── 02-secret-provider-class.yaml               # Azure Key Vault CSI SecretProviderClass
    ├── 02-secret.yaml.template                     # Fallback Secret template
    ├── 03-configmap.yaml                           # Non-sensitive runtime ConfigMap
    ├── 04-transaction-service.yaml                 # Deployment (3 replicas) & ClusterIP Service
    ├── 05-route.yaml                               # OpenShift TLS Edge Route
    ├── 06-servicemonitor.yaml                      # Prometheus ServiceMonitor (/actuator/prometheus)
    └── grafana/
        └── banking-platform-dashboard.json         # Pre-built Grafana dashboard
```

---

## 2. Key Enterprise Capabilities

- **Runtime**: Java 21 LTS, Spring Boot 3.4.0, Apache Camel 4.8.0
- **Integration Pattern**: Event-Driven Kafka consumer (`camel-kafka`) from topic `payments.v1` with consumer group load balancing across 3–5 replicas
- **Security**:
  - Spring Security HTTP Basic Authentication (`APP_SECURITY_USER` / `APP_SECURITY_PASSWORD`)
  - Secrets injected dynamically from **Azure Key Vault** via the Secrets Store CSI Driver ([`deployment/02-secret-provider-class.yaml`](deployment/02-secret-provider-class.yaml))
  - OpenShift Edge TLS Route termination ([`deployment/05-route.yaml`](deployment/05-route.yaml))
- **Observability**:
  - Structured JSON logs via `logstash-logback-encoder`
  - Micrometer + Prometheus metrics at `/actuator/prometheus` (route ID: `amq-transaction-consumer-route`)
  - Readiness (`/actuator/health/readiness`) and Liveness (`/actuator/health/liveness`) probes

---

## 3. Build & Run Instructions

### 3.1 Compile the Maven Artifact
From the `On-Prem-Microservice-2` directory:

```bash
mvn clean package -DskipTests -f transaction-service/pom.xml
```

### 3.2 Build the Container Image
The root [`Dockerfile`](Dockerfile) packages the compiled JAR onto `registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20`:

```bash
podman build -t transaction-service:1.0.0 .
```

### 3.3 Deploy to On-Premises OpenShift
Apply the manifests in [`deployment/`](deployment):

```bash
oc apply -f deployment/01-namespace.yaml
oc apply -f deployment/02-secret-provider-class.yaml
oc apply -f deployment/03-configmap.yaml
oc apply -f deployment/04-transaction-service.yaml
oc apply -f deployment/05-route.yaml
oc apply -f deployment/06-servicemonitor.yaml
```

### 3.4 Scale Instances (3 to 5 Replicas)
```bash
oc scale deployment/transaction-service --replicas=5 -n payment-app
```

---

## 4. Verification & Kafka Settlement Logs

### Inspect Real-Time Kafka Consumer Settlement Logs
```bash
oc logs deployment/transaction-service -n payment-app --tail=20
```

**Sample Structured JSON Log Output:**
```json
{"@timestamp":"2026-10-20T18:00:01.112Z","message":"Received payment event from AMQ Streams topic [payments.v1]: {\"paymentId\":\"PAY-1001\"...}","logger_name":"amq-transaction-consumer-route","level":"INFO"}
{"@timestamp":"2026-10-20T18:00:01.145Z","message":"Transaction settled: TX_ID=TX-92772515, Payment_ID=PAY-1001, Amount=2500.00 USD","logger_name":"com.example.onprem.transaction.routes.TransactionRouteBuilder","level":"INFO"}
```

### `GET /api/v1/transactions/status`
Checks the active status of the AMQ Streams settlement consumer:

```bash
TX_HOST=$(oc get route transaction-service -n payment-app -o jsonpath='{.spec.host}')

curl -s -k -u "${APP_SECURITY_USER}:${APP_SECURITY_PASSWORD}" \
  "https://${TX_HOST}/api/v1/transactions/status" | jq .
```

---

## 5. Related Documentation

- [Root Repository Overview](../README.md)
- [Enterprise Architecture Guide](../docs/ARCHITECTURE.md)
- [Azure Key Vault Secrets Integration](../docs/AZURE-KEY-VAULT-SECRETS.md)
- [Observability & Grafana Dashboards](../docs/OBSERVABILITY-AND-DASHBOARDS.md)
