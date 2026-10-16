# OpenShift Local (CRC) Quickstart Guide

This directory provides a **100% self-contained local testing deployment** on **Red Hat OpenShift Local (CRC)**.

It simulates the target On-Premises and ARO platforms on your laptop without requiring an active Azure subscription, Azure Key Vault, or external AMQ Streams Kafka broker.

---

## 1. Sizing and Starting OpenShift Local (CRC)

Before running the microservices, ensure CRC has sufficient memory and CPU allocated:

```bash
# 1. Configure CRC resources (12-14 GB RAM and 4-6 vCPUs recommended)
crc config set memory 14336
crc config set cpus 6

# 2. Start CRC
crc start

# 3. Add oc to your shell environment
eval $(crc oc-env)

# 4. Log in as kubeadmin (use the password output by 'crc start')
oc login -u kubeadmin https://api.crc.testing:6443
```

---

## 2. Architecture of the Local Deployment

| Component | Production Architecture | Local CRC Simulation |
| :--- | :--- | :--- |
| **Secrets Engine** | Azure Key Vault via Secrets Store CSI Driver | Native Kubernetes Secrets ([`02-local-secrets.yaml`](02-local-secrets.yaml)) |
| **Kafka Broker** | Multi-node AMQ Streams Kafka Cluster | Ephemeral single-pod KRaft Kafka ([`04-kafka-ephemeral.yaml`](04-kafka-ephemeral.yaml)) |
| **Container Images** | Enterprise Registry (Quay / ACR) | Built directly into CRC Internal Registry via OpenShift Binary BuildConfig |
| **Ingress / TLS** | Custom Edge Routes | Default CRC Wildcard Routes (`*.apps-crc.testing`) |
| **Observability** | Prometheus DB + Grafana | CRC User Workload Monitoring + `/actuator/prometheus` |

---

## 3. One-Command Deployment

Run the automated deployment script from the project root:

```bash
chmod +x deploy/local/*.sh
./deploy/local/deploy-local.sh
```

### What this script does:
1. Creates namespaces `payment-app` and `trade-app`.
2. Creates local developer secrets (`APP_SECURITY_USER=api-user`, `APP_SECURITY_PASSWORD=LocalCrcSecurePassword123!`).
3. Provisions the single-node ephemeral KRaft Kafka broker (`amq-cluster-kafka-bootstrap:9092`).
4. Compiles and builds the container images for all 4 microservices directly into the CRC internal registry using `oc start-build --from-dir`.
5. Deploys the microservice Deployments, Services, and OpenShift Routes.
6. Deploys the CoreOS `ServiceMonitor` resources for Prometheus.

---

## 4. Testing the Local Services

You can execute all tests with a single command using the automated test runner:

```bash
./deploy/local/test-local.sh
```

Or run manual test commands:

### Test 1: Ingest Payment into Kafka (`payment-service`)
```bash
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' \
  -X POST https://payment-service-payment-app.apps-crc.testing/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "PAY-1002",
    "sourceAccount": "ACC-1001",
    "destinationAccount": "ACC-2002",
    "amount": 1250.00,
    "currency": "USD",
    "description": "Invoice"
  }'
```
*Verify settlement processing in the consumer logs:*
```bash
oc logs deployment/transaction-service -n payment-app --tail=10
```

### Test 2: Query Portfolio Position (`portfolio-service`)
```bash
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' \
  https://portfolio-service-trade-app.apps-crc.testing/api/v1/portfolios/ACC-1001
```

### Test 3: Execute Trade via Inter-Service Camel HTTP Call (`trade-service`)
```bash
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' \
  -X POST https://trade-service-trade-app.apps-crc.testing/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-1001",
    "symbol": "AAPL",
    "side": "BUY",
    "quantity": 25,
    "price": 225.50
  }'
```

### Test 4: Query Prometheus Telemetry Endpoint
```bash
curl -s -k https://payment-service-payment-app.apps-crc.testing/actuator/prometheus | grep -E "camel_exchanges_total"
```

For full details, see [docs/LOCAL-CRC-DEPLOYMENT.md](../../docs/LOCAL-CRC-DEPLOYMENT.md).

---

## 5. Teardown

To delete all local workloads and namespaces:

```bash
./deploy/local/teardown-local.sh
```
