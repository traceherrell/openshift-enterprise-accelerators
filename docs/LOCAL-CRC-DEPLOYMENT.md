# OpenShift Local (CRC) Deployment Guide

This guide details the **100% self-contained local testing deployment** on **Red Hat OpenShift Local (CRC)**.

It simulates the target On-Premises and ARO platforms on a developer laptop without requiring an active Azure subscription, Azure Key Vault, or external AMQ Streams Kafka broker.

---

## 1. Prerequisites and CRC Sizing

Before deploying the workloads, ensure OpenShift Local has adequate resources:

```bash
# 1. Configure CRC resources (12–14 GB RAM and 4–6 vCPUs recommended)
crc config set memory 14336
crc config set cpus 6

# 2. Start CRC
crc start

# 3. Add oc to shell PATH
eval $(crc oc-env)

# 4. Log in as kubeadmin
oc login -u kubeadmin https://api.crc.testing:6443
```

---

## 2. Local Simulation Architecture

| Capability | Production Target | Local CRC Simulation |
| :--- | :--- | :--- |
| **Secrets Engine** | Azure Key Vault via Secrets Store CSI Driver | Native Kubernetes Secrets ([`deploy/local/02-local-secrets.yaml`](../deploy/local/02-local-secrets.yaml)) |
| **Kafka Broker** | Multi-node AMQ Streams Kafka cluster | Ephemeral single-pod KRaft Kafka ([`deploy/local/04-kafka-ephemeral.yaml`](../deploy/local/04-kafka-ephemeral.yaml)) |
| **Container Images** | Enterprise Registry (Quay.io / ACR) | Compiled locally with Maven and packaged directly into CRC internal registry |
| **Ingress / TLS** | Custom Edge Routes | Default CRC Wildcard Routes (`*.apps-crc.testing`) |
| **Observability** | Prometheus DB + Grafana | CoreOS ServiceMonitors ([`deploy/local/08-servicemonitor.yaml`](../deploy/local/08-servicemonitor.yaml)) |

---

## 3. Automated One-Command Deployment

Run the automated deployment script from the project root:

```bash
chmod +x deploy/local/*.sh
./deploy/local/deploy-local.sh
```

### Deployment Pipeline Steps:
1. Provisions namespaces `payment-app` and `trade-app`.
2. Creates local developer secrets (`APP_SECURITY_USER=api-user`, `APP_SECURITY_PASSWORD=LocalCrcSecurePassword123!`).
3. Provisions the single-node ephemeral KRaft Kafka broker (`amq-cluster-kafka-bootstrap:9092`).
4. Pre-compiles all four microservices locally with Maven into executable JARs.
5. Builds and tags container images directly in the CRC internal registry via OpenShift binary BuildConfigs.
6. Automatically cleans up build pods to prevent CRC VM disk exhaustion.
7. Deploys workloads, services, routes, and Prometheus `ServiceMonitor` definitions.

---

## 4. Verification and Automated Testing

Run the automated test runner:

```bash
./deploy/local/test-local.sh
```

### Manual Testing Commands:

```bash
# Test 1: Query Portfolio Position
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' \
  https://portfolio-service-trade-app.apps-crc.testing/api/v1/portfolios/ACC-1001

# Test 2: Execute Trade (Synchronous Camel HTTP)
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' -X POST \
  https://trade-service-trade-app.apps-crc.testing/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{"accountId":"ACC-1001","symbol":"MSFT","side":"BUY","quantity":10,"price":420.00}'

# Test 3: Ingest Payment into Kafka (Payment Service)
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' -X POST \
  https://payment-service-payment-app.apps-crc.testing/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"PAY-1002","sourceAccount":"ACC-1001","destinationAccount":"ACC-2002","amount":1250.00,"currency":"USD","description":"Invoice"}'

# Test 4: Verify Transaction Settlement Logs
oc logs deployment/transaction-service -n payment-app --tail=10

# Test 5: Scrape Prometheus Metrics
curl -s -k https://payment-service-payment-app.apps-crc.testing/actuator/prometheus | grep -E "camel_exchanges_total"
```

---

## 5. Teardown

To delete all local workloads and namespaces:

```bash
./deploy/local/teardown-local.sh
```
